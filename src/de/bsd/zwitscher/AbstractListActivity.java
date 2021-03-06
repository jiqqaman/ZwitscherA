package de.bsd.zwitscher;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Superclass for Tabs with list stuff
 * @author Heiko W. Rupp
 */
public abstract class AbstractListActivity extends ListActivity {

    ProgressBar pg;
    TextView titleTextBox;
    TwitterHelper th;
    TweetDB tdb;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity theParent = getParent();
        if (theParent instanceof TabWidget) {
            TabWidget parent = (TabWidget) theParent;
            pg = parent.pg;
            titleTextBox = parent.titleTextBox;
        }

        th = new TwitterHelper(this);
        tdb = new TweetDB(this,0); // TODO set correct account

    }

    /**
     * Scrolls to top, called from the ToTop button
     * @param v
     */
    @SuppressWarnings("unused")
    public void scrollToTop(View v) {
        getListView().setSelection(0);
    }

    /**
     * Called from the post button
     * @param v
     */
    @SuppressWarnings("unused")
    public void post(View v) {
        Intent i = new Intent(this, NewTweetActivity.class);
        startActivity(i);
    }

    abstract public void reload(View v);
}
