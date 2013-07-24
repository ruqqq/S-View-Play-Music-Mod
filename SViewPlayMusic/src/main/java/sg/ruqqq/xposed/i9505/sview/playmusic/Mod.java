package sg.ruqqq.xposed.i9505.sview.playmusic;

import static de.robv.android.xposed.XposedHelpers.getObjectField;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XModuleResources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Mod implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {
	static final String TAG = "SViewPlayMusic/Mod";

	static final int MIN_DISTANCE = 100;
    private PointerCoords mDownPos = new PointerCoords();
    private PointerCoords mUpPos = new PointerCoords();
	
	private Context mContext = null;
	private TextView mTrackTitle = null;
	private ImageView mAlbumArtWithImage = null;
	
	private Object mMusicWidgetObject = null;

	private static Intent mMetaChangedIntent;
	//private static Intent mAAIntent;
	private static Intent mPlayStateChangedIntent;

	private static boolean isPlaying = false;
    private static long mSongId;
    private static String mTrackTitleString = "";
	private static String mArtistNameString = "";
    private static long mAlbumId;
	private static Bitmap mAlbumArt = null;
	
	private LinearLayout mClockView;

    private static String MODULE_PATH;
    //private static XModuleResources mModRes;

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }

	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("android"))
			return;
		
		XposedBridge.log("[" + TAG + "] Initializing S View hooks...");
		
		Class<?> SViewCoverManager = XposedHelpers.findClass("com.android.internal.policy.impl.sviewcover.SViewCoverManager",
				lpparam.classLoader);
		
		XposedBridge.hookAllConstructors(SViewCoverManager, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				if (mContext == null)
					mContext = (Context) getObjectField(param.thisObject, "mContext");

                registerAndLoadStatus();
			}
		});
		
		Class<?> MusicWidget = XposedHelpers.findClass("com.android.internal.policy.impl.sviewcover.SViewCoverWidget$MusicWidet",
				lpparam.classLoader);
		Class<?> ClockWidget = XposedHelpers.findClass("com.android.internal.policy.impl.sviewcover.SViewCoverWidget$Clock",
				lpparam.classLoader);
		
		XposedHelpers.findAndHookMethod(MusicWidget, "onFinishInflate", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				mMusicWidgetObject = param.thisObject;
				
				mTrackTitle = (TextView) getObjectField(param.thisObject, "mTrackTitle");
				mAlbumArtWithImage = (ImageView) getObjectField(param.thisObject, "mAlbumArtWithImage");
				
				updateRemoteFieldsFromLocalFields();
				
				if (isPlaying) {
					setVisibilityOfMusicWidgets(View.VISIBLE);
					setTrackTitleText(getTextToSet(mTrackTitleString, mArtistNameString));
					if (mAlbumArt != null) {
						mAlbumArtWithImage.setImageBitmap(mAlbumArt);
					}
				} else {
					setVisibilityOfMusicWidgets(View.GONE);
				}
			}
		});
		
		XposedHelpers.findAndHookMethod(ClockWidget, "onFinishInflate", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				mClockView = (LinearLayout) getObjectField(param.thisObject, "mClockView");
				mClockView.setOnTouchListener(new OnTouchListener() {
					
					@Override
					public boolean onTouch(View v, MotionEvent event) {
				        switch(event.getAction()) {
			            // Capture the position where swipe begins
			            case MotionEvent.ACTION_DOWN: {
			                event.getPointerCoords(0, mDownPos);
			                return true;
			            }
			 
			            // Get the position where swipe ends
			            case MotionEvent.ACTION_UP: {
			                event.getPointerCoords(0, mUpPos);
			 
			                float dx = mDownPos.x - mUpPos.x;
			 
			                // Check for horizontal wipe
			                if (Math.abs(dx) > MIN_DISTANCE) {
			                    if (dx > 0)
			                        onSwipeLeft();
			                    else
			                        onSwipeRight();
			                    return true;
			                }
			 
			                float dy = mDownPos.y - mUpPos.y;
			 
			                // Check for vertical wipe
			                if (Math.abs(dy) > MIN_DISTANCE) {
			                    if (dy > 0)
			                        onSwipeUp();
			                    else
			                        onSwipeDown();
			                    return true;
			                }
			            }
			        }
			        return false;
					}

					private void onSwipeDown() {
                        if (mContext != null) {
                            String command = "togglepause";
                            Intent i = new Intent("com.android.music.musicservicecommand");
                            i.putExtra("command", command);
                            mContext.sendBroadcast(i);
                        }
					}

					private void onSwipeUp() {
                        if (mContext != null) {
                            String command = "togglepause";
                            Intent i = new Intent("com.android.music.musicservicecommand");
                            i.putExtra("command", command);
                            mContext.sendBroadcast(i);
                        }
					}

					private void onSwipeRight() {
						if (mContext != null) {
                            String command = "next";
                            Intent i = new Intent("com.android.music.musicservicecommand");
                            i.putExtra("command", command);
                            mContext.sendBroadcast(i);
						}
					}

					private void onSwipeLeft() {
						if (mContext != null) {
                            String command = "previous";
                            Intent i = new Intent("com.android.music.musicservicecommand");
                            i.putExtra("command", command);
                            mContext.sendBroadcast(i);
						}
					}
				});
			}
		});
	}

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!resparam.packageName.equals("android"))
            return;

        //mModRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
    }

	private String getTextToSet(String title, String artist) {
		return title + " - " + artist;
	}
	
	private void setTrackTitleText(String text) {
		if (mTrackTitle != null) {
			mTrackTitle.setText(text);
			mTrackTitle.setSelected(true);
			if (!text.isEmpty()) {
				setVisibilityOfMusicWidgets(View.VISIBLE);
			}

            XposedBridge.log("[" + TAG + "] setTrackTitleText");
		}
	}
	
	private void setAlbumArt(Bitmap bitmap) {
		mAlbumArt = bitmap;
		
		if (mAlbumArtWithImage != null)
			mAlbumArtWithImage.setImageBitmap(bitmap);
	}
	
	private void updateRemoteFieldsFromLocalFields() {
		setTrackTitleText(getTextToSet(mTrackTitleString, mArtistNameString));
		setAlbumArt(mAlbumArt);
		
		if (mMusicWidgetObject != null) {
			XposedHelpers.setObjectField(mMusicWidgetObject, "currentTitle", mTrackTitleString);
			XposedHelpers.setObjectField(mMusicWidgetObject, "currentArtist", mArtistNameString);
			XposedHelpers.setObjectField(mMusicWidgetObject, "mAlbumArtBitmap", mAlbumArt);
			XposedHelpers.setBooleanField(mMusicWidgetObject, "mIsPlaying", isPlaying);
		}
	}
	 
	private void registerAndLoadStatus() {
		XposedBridge.log("[" + TAG + "] Register broadcast receivers");
		
		if (mContext == null)
			return;
		
		//mAAIntent = mContext.registerReceiver(mAAReceiver, new IntentFilter());
		mMetaChangedIntent = mContext.registerReceiver(mTrackReceiver, new IntentFilter("com.android.music.metachanged"));
		mPlayStateChangedIntent = mContext.registerReceiver(mStatusReceiver, new IntentFilter("com.android.music.playstatechanged"));
		
		XposedBridge.log("[" + TAG + "] Successfully registered receivers");
	}
	
	private BroadcastReceiver mTrackReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
            XposedBridge.log("[" + TAG + "] mMetaChangedIntent: " + intent);
			mMetaChangedIntent = intent;
			if (mMetaChangedIntent != null) {
				updateTrackUI();
			}
		}
	};
	
	/*private BroadcastReceiver mAAReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			mAAIntent = intent;
			updateAlbumArt();
			XposedBridge.log("[" + TAG + "] mAAReceiver " + intent);
		}
	};*/
	
	private BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			mPlayStateChangedIntent = intent;
            XposedBridge.log("[" + TAG + "] mStatusReceiver: " + intent);
			updateStatusUI();
		}
	};
	
	private void setVisibilityOfMusicWidgets(int visibility) {
		if (mTrackTitle != null) {
			mTrackTitle.setVisibility(visibility);
			mTrackTitle.setSelected(true);
		}
		
		if (mAlbumArtWithImage != null)
			mAlbumArtWithImage.setVisibility(visibility);

        XposedBridge.log("[" + TAG + "] setVisibilityOfMusicWidgets: " + (visibility==View.VISIBLE));
	}

	protected void updateAlbumArt() {
		/*String directAAPath = mAAIntent.getStringExtra(PowerAMPiAPI.ALBUM_ART_PATH);

		 if (mAAIntent.hasExtra(PowerAMPiAPI.ALBUM_ART_BITMAP)) {
			Bitmap albumArtBitmap = mAAIntent.getParcelableExtra(PowerAMPiAPI.ALBUM_ART_BITMAP);
			if (albumArtBitmap != null) {
				setAlbumArt(albumArtBitmap);
			}
		} else 	if (!TextUtils.isEmpty(directAAPath)) {
			if (mAlbumArtWithImage != null) {
				mAlbumArtWithImage.setImageURI(Uri.parse(directAAPath));
			}
		} else {
			setAlbumArt(null);
		}*/
		 
		 updateRemoteFieldsFromLocalFields();
	}

	protected void updateStatusUI() {
		if (mPlayStateChangedIntent != null) {
			boolean playing = mPlayStateChangedIntent.getBooleanExtra("playing", false);
			if (mMusicWidgetObject != null)
				XposedHelpers.setBooleanField(mMusicWidgetObject, "mIsPlaying", playing);
			
			if (!playing) {
				setVisibilityOfMusicWidgets(View.GONE);
			} else {
				setVisibilityOfMusicWidgets(View.VISIBLE);
			}
			
			isPlaying = playing;
		}

        updateRemoteFieldsFromLocalFields();
        XposedBridge.log("[" + TAG + "] updateStatusUI");
	}

	private void updateTrackUI() {
		String textToSet = "";
		if (mMetaChangedIntent != null) {
            mSongId = mMetaChangedIntent.getLongExtra("id", -1);
            mTrackTitleString = mMetaChangedIntent.getStringExtra("track");
            mArtistNameString = mMetaChangedIntent.getStringExtra("artist");
            mAlbumId = mMetaChangedIntent.getLongExtra("albumId", -1);

            // Album art code
            /*if (mContext != null && mSongId != -1 && mAlbumId != -1) {
                mAlbumArt = MusicUtils.getArtwork(mContext, mSongId, mAlbumId, false);
                setAlbumArt(mAlbumArt);
            }*/
			textToSet = getTextToSet(mTrackTitleString, mArtistNameString);
		} else {
			mTrackTitleString = mArtistNameString = "";
		}
		
		setTrackTitleText(textToSet);
		updateRemoteFieldsFromLocalFields();

        XposedBridge.log("[" + TAG + "] updateTrackUI");
	}

    // Stolen from http://androidhosting.org/Devs/BKJolly/Test/AlbumArtTile.java
    // CRASHES SYSTEM
    private static class MusicUtils {
        private static int sArtId = -2;
        private static Bitmap mCachedBit = null;
        private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
        private static final BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
        private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        private static final Uri MUSIC_CONTENT_URI = Uri.parse("content://com.google.android.music.MusicContent");

        public static final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
        public static final String META_CHANGED = "com.android.music.metachanged";

        static {
            // for the cache,
            // 565 is faster to decode and display
            // and we don't want to dither here because the image will be scaled down later
            sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.RGB_565;
            sBitmapOptionsCache.inDither = false;

            sBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            sBitmapOptions.inDither = false;
        }

        // get album art for specified file
        private static final String sExternalMediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString();
        private static Bitmap getArtworkFromFile(Context context, long songid, long albumid) {
            Bitmap bm = null;

            if (albumid < 0 && songid < 0) {
                throw new IllegalArgumentException("Must specify an album or a song id");
            }

            try {
                if (albumid < 0) {
                    // for online music
                    XposedBridge.log("[" + TAG + "] looking here for album art:" + MUSIC_CONTENT_URI + "/albumart/" + songid);
                    Uri uri = Uri.withAppendedPath(MUSIC_CONTENT_URI, "albumart/"+songid);
                    ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        FileDescriptor fd = pfd.getFileDescriptor();
                        bm = BitmapFactory.decodeFileDescriptor(fd);
                    }
                } else {
                    // local music
                    Uri uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                    XposedBridge.log("[" + TAG + "] looking here for album art:" + uri.toString());
                    ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        FileDescriptor fd = pfd.getFileDescriptor();
                        bm = BitmapFactory.decodeFileDescriptor(fd);
                    }
                }
            } catch (IllegalStateException ex) {
            } catch (FileNotFoundException ex) {
            }
            if (bm != null) {
                mCachedBit = bm;
            }
            return bm;
        }

        public static Bitmap getArtwork(Context context, long song_id, long album_id) {
            return getArtwork(context, song_id, album_id, true);
        }

        /** Get album art for specified album. You should not pass in the album id
         * for the "unknown" album here (use -1 instead)
         */
        public static Bitmap getArtwork(Context context, long song_id, long album_id,
                                        boolean allowdefault) {

            if (album_id < 0) {
                // This is something that is not in the database, so get the album art directly
                // from the file.
                if (song_id >= 0) {
                    Bitmap bm = getArtworkFromFile(context, song_id, -1);
                    if (bm != null) {
                        return bm;
                    }
                }
                /*if (allowdefault) {
                    return getDefaultArtwork(context);
                }*/
                return null;
            }

            ContentResolver res = context.getContentResolver();
            Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
            if (uri != null) {
                InputStream in = null;
                try {
                    in = res.openInputStream(uri);
                    return BitmapFactory.decodeStream(in, null, sBitmapOptions);
                } catch (FileNotFoundException ex) {
                    // The album art thumbnail does not actually exist. Maybe the user deleted it, or
                    // maybe it never existed to begin with.
                    Bitmap bm = getArtworkFromFile(context, song_id, album_id);
                    if (bm != null) {
                        if (bm.getConfig() == null) {
                            bm = bm.copy(Bitmap.Config.RGB_565, false);
                            /*if (bm == null && allowdefault) {
                                return getDefaultArtwork(context);
                            }*/
                        }
                    }/* else if (allowdefault) {
                        bm = getDefaultArtwork(context);
                    }*/
                    return bm;
                } finally {
                    try {
                        if (in != null) {
                            in.close();
                        }
                    } catch (IOException ex) {
                    }
                }
            }

            return null;
        }

        private static Bitmap getArtworkFromUrl(Context context, String address) {
            Bitmap bm = null;
            try{
                URL url = new URL(address);
                InputStream content = (InputStream)url.getContent();
                Drawable d = Drawable.createFromStream(content , "src");
                bm = BitmapFactory.decodeStream(content);
            }catch(Exception e){
                e.printStackTrace();
            }

            return bm;
        }

        /*private static Bitmap getDefaultArtwork(Context context) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            if (mModRes != null)
                return BitmapFactory.decodeStream(
                    mModRes.openRawResource(R.drawable.albumart_mp_unknown), null, opts);
            else
                return null;
        }*/
    }
}
