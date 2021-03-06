package de.bsd.zwitscher;


import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Html;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import com.google.api.translate.Language;
import com.google.api.translate.Translate;

import de.bsd.zwitscher.helper.NetworkHelper;
import de.bsd.zwitscher.helper.PicHelper;
import twitter4j.Place;
import twitter4j.Status;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.view.View;
import twitter4j.User;

import java.io.BufferedInputStream;
import java.net.URL;
import java.util.*;

/**
 * This Activity displays one individual status.
 * Layout definition is in res/layout/single_tweet
 *
 * @author Heiko W. Rupp
 */
public class OneTweetActivity extends Activity implements OnInitListener, OnUtteranceCompletedListener {

	Context ctx = this;
	Status status ;
    ImageView userPictureView;
    ProgressBar pg;
    boolean downloadPictures=false;
    TextToSpeech tts;
    TextView titleTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);

		setContentView(R.layout.single_tweet);
        setupspeak();

        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,R.layout.window_title);
        pg = (ProgressBar) findViewById(R.id.title_progress_bar);
        titleTextView = (TextView) findViewById(R.id.title_msg_box);

        userPictureView = (ImageView) findViewById(R.id.UserPictureImageView);

        NetworkHelper networkHelper = new NetworkHelper(this);
        downloadPictures = networkHelper.mayDownloadImages();

        Intent intent = getIntent();
        String dataString = intent.getDataString();
        Bundle bundle = intent.getExtras();

        // If this is not null, we are called from another app
        if ( dataString!=null) {
            if (dataString.matches("http://twitter.com/.*/status/.*$")) {
                String tids = dataString.substring(dataString.lastIndexOf("/")+1);
                Long tid = Long.parseLong(tids);
                TwitterHelper th = new TwitterHelper(this);
                status = th.getStatusById(tid,0L,false,false);
            } else if (dataString.matches("http://twitter.com/#!/.*$")) {
                // A user - forward to UserDetailActivity TODO: remove once this is coded in AndroidManifest.xml
                String userName = dataString.substring(dataString.lastIndexOf("/")+1);
                Intent i = new Intent(this,UserDetailActivity.class);
                i.putExtra("userName",userName);
                startActivity(i);
                finish();
            } else if (dataString.matches("http://twitter.com/[a-zA-Z0-9_]*\\?.*")) {
                // A user ref in email - forward to UserDetailActivity TODO: remove once this is coded in AndroidManifest.xml
                String userName = dataString.substring(19,dataString.indexOf("?"));
                Intent i = new Intent(this,UserDetailActivity.class);
                i.putExtra("userName",userName);
                startActivity(i);
                finish();
            }

        } else {
            // Called from within Zwitscher
            status = (Status) bundle.get(getString(R.string.status));
        }


        if (status==null) {
            // e.g. when called from HTC Mail, which fails to forward the full
            // http://twitter.com/#!/.../status/.. url, but only sends http://twitter.com
            Log.w("OneTweetActivity","Status was null for Intent " + intent );
            if (tts!=null)
                tts.shutdown();

            finish();
            return;
        }

        Log.i("OneTweetActivity","Showing status: " + status.toString());

        // Download the user profile image in a background task, as this may
        // mean a network call.
        if (status.getRetweetedStatus()==null)
            new DownloadUserImageTask().execute(status.getUser());
        else
            new DownloadUserImageTask().execute(status.getRetweetedStatus().getUser());

        // Check if the tweet contains urls to picture services and load thumbnails
        // if needed
        List<UrlPair> pictureUrls = parseForPictureUrls(status);
        if (!pictureUrls.isEmpty())
            new DownloadImagePreviewsTask(this).execute(pictureUrls);

        TextView tv01 = (TextView) findViewById(R.id.TextView01);
        StringBuilder sb = new StringBuilder("<b>");
        if (status.getRetweetedStatus()==null) {
            sb.append(status.getUser().getName());
            sb.append(" (");
            sb.append(status.getUser().getScreenName());
            sb.append(")");
            sb.append("</b>");
        }
        else {
            sb.append(status.getRetweetedStatus().getUser().getName());
            sb.append(" (");
            sb.append(status.getRetweetedStatus().getUser().getScreenName());
            sb.append(" )</b> ").append(getString(R.string.resent_by)).append(" <b>");
            sb.append(status.getUser().getName());
            sb.append("</b>");
        }
        tv01.setText(Html.fromHtml(sb.toString()));

        TextView mtv = (TextView) findViewById(R.id.MiscTextView);
        if (status.getInReplyToScreenName()!=null) {
            String s = getString(R.string.in_reply_to);
            mtv.setText(Html.fromHtml(s + " <b>" + status.getInReplyToScreenName() + "</b>"));
        }
        else {
            mtv.setText("");
        }

        TextView tweetView = (TextView)findViewById(R.id.TweetTextView);
        tweetView.setText(status.getText());

        TextView timeCientView = (TextView)findViewById(R.id.TimeTextView);
        TwitterHelper th = new TwitterHelper(this);
        String s = getString(R.string.via);
        String text = th.getStatusDate(status) + s + status.getSource();
        String from = getString(R.string.from);
        if (status.getPlace()!=null) {
            Place place = status.getPlace();
            text += " " + from + " " + place.getFullName();
        }
        timeCientView.setText(Html.fromHtml(text));


        // Update Button state depending on Status' properties
        ImageButton threadButton = (ImageButton) findViewById(R.id.ThreadButton);
        if (status.getInReplyToScreenName()==null) {
            threadButton.setEnabled(false);
        }

        ImageButton favoriteButton = (ImageButton) findViewById(R.id.FavoriteButton);
        if (status.isFavorited())
            favoriteButton.setImageResource(R.drawable.favorite_on);

        ImageButton translateButon = (ImageButton) findViewById(R.id.TranslateButton);
        translateButon.setEnabled(networkHelper.isOnline());
	}

    /**
     * Display display of the details of a user from pressing
     * the user icon button.
     * @param v
     */
    @SuppressWarnings("unused")
    public void displayUserDetail(View v) {
        Intent i = new Intent(getApplicationContext(), UserDetailActivity.class);
        User theUser;
        if (status.getRetweetedStatus()==null) {
            theUser = status.getUser();
        } else {
            theUser = status.getRetweetedStatus().getUser();
        }
        i.putExtra("userName", theUser.getName());
        i.putExtra("userId",theUser.getId());
        startActivity(i);
    }

    /**
     * Trigger replying to the current status.
     * @param v
     */
    @SuppressWarnings("unused")
	public void reply(View v) {
		Intent i = new Intent(getApplicationContext(), NewTweetActivity.class);
		i.putExtra(getString(R.string.status), status);
		i.putExtra("op", getString(R.string.reply));
		startActivity(i);

	}

    /**
     * Trigger replying to all users mentioned via @xxx in the
     * current status. Opens an editor Window first
     * @param v
     */
    @SuppressWarnings("unused")
	public void replyAll(View v) {
		Intent i = new Intent(getApplicationContext(), NewTweetActivity.class);
		i.putExtra(getString(R.string.status), status);
		i.putExtra("op", getString(R.string.replyall));
		startActivity(i);

	}

    /**
     * Trigger a resent of the current status
     * @param v
     */
    @SuppressWarnings("unused")
	public void retweet(View v) {
        UpdateRequest request = new UpdateRequest(UpdateType.RETWEET);
        request.id = status.getId();
        new UpdateStatusTask(this,pg).execute(request);
	}


    /**
     * Do the classical re-send thing by prefixing with 'RT'.
     * Opens an editor window first.
     * @param v
     */
    @SuppressWarnings("unused")
	public void classicRetweet(View v) {
		Intent i = new Intent(getApplicationContext(), NewTweetActivity.class);
		i.putExtra(getString(R.string.status), status);
		i.putExtra("op", getString(R.string.classicretweet));
		startActivity(i);

	}

    /**
     * Starts a view that shows the conversation around the current
     * status.
     * @param v
     */
    @SuppressWarnings("unused")
    public void threadView(View v) {
        TwitterHelper th = new TwitterHelper(ctx);

        Intent i = new Intent(getApplicationContext(),ThreadListActivity.class);
        i.putExtra("startId", status.getId());
        startActivity(i);
    }

    /**
     * Marks the current status as (non) favorite
     * @param v
     */
    @SuppressWarnings("unused")
    public void favorite(View v) {
        TwitterHelper th = new TwitterHelper(ctx);

        ImageButton favoriteButton = (ImageButton) findViewById(R.id.FavoriteButton);

        UpdateRequest request = new UpdateRequest(UpdateType.FAVORITE);
        request.status = status;
        request.view = favoriteButton;

        new UpdateStatusTask(this,pg).execute(request);

    }

    /**
     * Start sending a direct message to the user that sent this
     * status.
     * @param v
     */
    @SuppressWarnings("unused")
    public void directMessage(View v) {
        Intent i = new Intent(getApplicationContext(), NewTweetActivity.class);
        i.putExtra(getString(R.string.status), status);
        i.putExtra("op", getString(R.string.direct));
        startActivity(i);

    }


    //////////////// speak related stuff ////////////////////

    public void onInit(int status) {
        String statusString = status == 0 ? "Success" : "Failure";
        System.out.println("speak"+" onInit " + statusString);
    }

    public void onUtteranceCompleted(String utteranceId) {
        Log.i("speak", "Utterance done: " + utteranceId);

    }

    /**
     * Setup speak just in case we may need it.
     * If directly called from within speak() it will not work
     * because the onInit() listener is no ready early enough
     */
	public void setupspeak() {

		tts = new TextToSpeech(this,this);
        tts.setLanguage(Locale.US);
    }

    /**
     * Speak the current status via TTS
     */
    @SuppressWarnings("unused")
    public void speak(View v)
    {
        int res = tts.setOnUtteranceCompletedListener(this);
        if (res==TextToSpeech.ERROR) {
            Log.e("1TA", "Failed to set on utterance listener");
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
		ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tweet_status_msg" + status.getUser().getScreenName());
		tts.speak(status.getText(), TextToSpeech.QUEUE_FLUSH, ttsParams);

	}


    //////////////// speak related stuff end ////////////////////

    /**
     * Translate the current status by calling Google translate.
     * Target language is the users current locale.
     * @param v
     */
    @SuppressWarnings("unused")
	public void translate(View v) {
		Translate.setHttpReferrer("http://bsd.de/zwitscher");
		try {
            Language targetLanguage;
            String locale = Locale.getDefault().getLanguage();
            targetLanguage = Language.fromString(locale);
			String result = Translate.execute(status.getText(), Language.AUTO_DETECT, targetLanguage);
			AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
			builder.setMessage(result);
			builder.setTitle("Translation result");
			builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(getApplicationContext(), e.getMessage(), 15000).show();
		}
	}

    /**
     * Finishes this screen and returns to the list of statuses
     * @param v
     */
    @SuppressWarnings("unused")
	public void done(View v) {
        if (tts!=null)
            tts.shutdown();
		finish();
	}


    /**
     * Return urls for thumbnails of linked images if the url in the status is recognized
     * as an image service
     * @param status Status to analyze
     * @return List of Bitmaps to display
     */
    private List<UrlPair> parseForPictureUrls(Status status) {
        URL[] urlArray = status.getURLs();
        Set<String> urls = new HashSet<String>();
        if (urlArray!=null) {
            for (URL url : urlArray) {
                urls.add(url.toString());
            }
        }

        // Nothing provided by twitter, so parse the text
        if (urls.size()==0) {
            String[] tokens = status.getText().split(" ");
            for (String token : tokens) {
                if (token.startsWith("http://") || token.startsWith("https://")) {
                    urls.add(token);
                }
            }
        }
        if (urls.size()==0)
            return Collections.emptyList();

        ArrayList<UrlPair> urlPairs = new ArrayList<UrlPair>(urls.size());


        // We have urls, so check for picture services
        for (String url :  urls) {
            Log.d("One tweet","Url = " + url);
            String finalUrlString;
            if (url.contains("yfrog.com")) {
                finalUrlString = url + ".th.jpg";
            }
            else if (url.contains("twitpic.com")) {
                String tmp = url;
                tmp = tmp.substring(tmp.lastIndexOf("/")+1);
                finalUrlString = "http://twitpic.com/show/thumb/" + tmp;
            }
            else if (url.contains("plixi.com")) {
                finalUrlString = "http://api.plixi.com/api/tpapi.svc/imagefromurl?size=thumbnail&url=" +  url;
            }
            else {
                Log.d("OTA::loadThumbnails", "Url " + url + " not supported for preview");
                continue;
            }
            UrlPair pair = new UrlPair(url,finalUrlString);
            urlPairs.add(pair);
        }
        return urlPairs;
    }

    /**
     * Load thumbnails of linked images for the passed listOfUrlPairs
     * @param listOfUrlPairs list of images to load (thumbnail url, url to full img)
     * @return List of bitmaps along
     */
    private List<BitmapWithUrl> loadThumbnails(List<UrlPair> listOfUrlPairs) {

        List<BitmapWithUrl> bitmaps = new ArrayList<BitmapWithUrl>(listOfUrlPairs.size());

        for (UrlPair urlPair : listOfUrlPairs) {
            Log.i("loadThumbail", "URL to load is " + urlPair.thumbnailUrl);

            try {
                URL picUrl = new URL(urlPair.thumbnailUrl);
                BufferedInputStream in = new BufferedInputStream(picUrl.openStream());
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                in.close();
                BitmapWithUrl bwu = new BitmapWithUrl(bitmap,urlPair.fullUrl);
                bitmaps.add(bwu);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return bitmaps;
    }



    /**
     * Background task to download the user profile images.
     */
    private class DownloadUserImageTask extends AsyncTask<User, Void,Bitmap> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pg.setVisibility(ProgressBar.VISIBLE);
        }

        protected Bitmap doInBackground(User... users) {

            User user = users[0];
            PicHelper picHelper = new PicHelper();
            Bitmap bi;
            if (downloadPictures)
                bi = picHelper.fetchUserPic(user);
            else
                bi = picHelper.getBitMapForUserFromFile(user);
            return bi;
        }


        protected void onPostExecute(Bitmap result) {
        	if (result!=null)
        		userPictureView.setImageBitmap(result);
            pg.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    /**
     * Background task to download the thumbnails of linked images
     */
    private class DownloadImagePreviewsTask extends AsyncTask<List<UrlPair>,Void,List<BitmapWithUrl>> {

        private Context context;

        private DownloadImagePreviewsTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pg.setVisibility(ProgressBar.VISIBLE);
            String text = getString(R.string.get_preview);
            titleTextView.setText(text);
        }

        @Override
        protected List<BitmapWithUrl> doInBackground(List<UrlPair>... urls) {
            List<BitmapWithUrl> bitmapList=null;
            if (downloadPictures)
                bitmapList = loadThumbnails(urls[0]);
            return bitmapList;
        }

        @Override
        protected void onPostExecute(final List<BitmapWithUrl> bitmaps) {
            super.onPostExecute(bitmaps);
            if (bitmaps!=null) {
                Gallery g = (Gallery) findViewById(R.id.gallery);
                ImageAdapter adapter = new ImageAdapter(context);

                adapter.addImages(bitmaps);
                g.setAdapter(adapter);
                g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        String url = bitmaps.get(position).url;
                        Uri uri = Uri.parse(url);
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(i);
                    }
                });
            }

            titleTextView.setText("");
            pg.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    /**
     * Picture adapter for the Gallery that holds the images
     * It is filled via data obtained via #loadThumbnails
     */
    private class ImageAdapter extends BaseAdapter {

        private List<BitmapWithUrl> mImages = new ArrayList<BitmapWithUrl>();

        void addImages(List<BitmapWithUrl> images) {
            mImages.addAll(images);
        }

        int mGalleryItemBackground;

        public ImageAdapter(Context c) {
            mContext = c;
            // See res/values/style.xml for the <declare-styleable> that defines
            // Gallery1.
            TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
            mGalleryItemBackground = a.getResourceId(
                    R.styleable.Gallery1_android_galleryItemBackground, 0);
            a.recycle();
        }

        public int getCount() {
            return mImages.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(mContext);

            Bitmap bitmap = mImages.get(position).bitmap;
            if (bitmap!=null) {
                i.setImageBitmap(bitmap);
                i.setScaleType(ImageView.ScaleType.FIT_XY);
                i.setLayoutParams(new Gallery.LayoutParams(bitmap.getWidth()*2, bitmap.getHeight()*2));
            }
            // The preferred Gallery item background
            i.setBackgroundResource(mGalleryItemBackground);

            return i;
        }

        private Context mContext;
    }

    /**
     * Helper class that holds a bitmap along with the picture url,
     * so that a click on the image in the gallery can start a browser
     * window to the image service to browser the full size one.
     */
    private class BitmapWithUrl {
        Bitmap bitmap;
        String url;

        public BitmapWithUrl(Bitmap bitmap, String picUrlString) {
            this.bitmap = bitmap;
            this.url = picUrlString;
        }
    }

    /**
     * Helper that just holds the urls of the full image service
     * url and the link to the thumbnail
     */
    private class UrlPair {
        String fullUrl;
        String thumbnailUrl;

        private UrlPair(String fullImageUrl, String thumbnailUrl) {
            this.fullUrl = fullImageUrl;
            this.thumbnailUrl = thumbnailUrl;
        }
    }
}
