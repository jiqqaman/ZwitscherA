package de.bsd.zwitscher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import de.bsd.zwitscher.helper.CaseInsensitiveStringComparator;
import de.bsd.zwitscher.helper.MetaList;
import twitter4j.Paging;
import twitter4j.SavedSearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Display the list of user lists
 *
 * @author Heiko W. Rupp
 */
public class ListOfListsActivity extends AbstractListActivity {

    Set<Map.Entry<String, Integer>> userListsEntries;
    int mode;
    ArrayAdapter<String> adapter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mode = getIntent().getIntExtra("list",0);

        setContentView(R.layout.tweet_list_layout);

        ImageButton reloadButton = (ImageButton) findViewById(R.id.tweet_list_reload_button);
        if (mode==0)
            reloadButton.setEnabled(true);
        else
            reloadButton.setEnabled(false);  // disabled for stored searches (for now), as the tweets are not persisted
    }


    @Override
    protected void onResume() {
        super.onResume();

        List<String> result = new ArrayList<String>();

        if (mode==0) {
            userListsEntries = tdb.getLists().entrySet();
            for (Map.Entry<String, Integer> userList : userListsEntries) {
                result.add(userList.getKey());
            }
            if (result.isEmpty()) {
                String s = getString(R.string.please_sync_lists);
                result.add(s);
            }
        }
        else if (mode==1) {
            List<SavedSearch> searches = th.getSavedSearchesFromDb();
            for (SavedSearch search : searches) {
                result.add(search.getName());
            }

            if (result.isEmpty()) {
                String s = getString(R.string.no_searches_found);
                result.add(s);
            }
        }
        else
            throw new IllegalArgumentException("Unknown mode " + mode);

        Collections.sort(result, new CaseInsensitiveStringComparator());
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, result);
        setListAdapter(adapter);

    }

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the
     * data associated with the selected item.
     *
     * @param l        The ListView where the click happened
     * @param v        The view that was clicked within the ListView
     * @param position The position of the view in the list
     * @param id       The row id of the item that was clicked
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        String text = (String) getListView().getItemAtPosition(position);

        if (mode==0) {
            int listId = -1 ;
            for (Map.Entry<String,Integer> userList : userListsEntries) {
                if (userList.getKey().equals(text))
                    listId = userList.getValue();
            }

            if (listId!=-1) {
                Intent intent = new Intent().setClass(this,TweetListActivity.class);
                intent.putExtra(TabWidget.LIST_ID, listId);

                startActivity(intent);
            }
        } else if (mode ==1) {
            List<SavedSearch> searches = th.getSavedSearchesFromDb();
            for (SavedSearch search : searches) {
                if (text.equals(search.getName())) {
                    Intent intent = new Intent().setClass(this,TweetListActivity.class);
                    intent.putExtra(TabWidget.LIST_ID, (-search.getId()));
                    Log.d("ListsOfLists", "Saved search with id " + search.getId());

                    startActivity(intent);
                }
            }
        }
    }

    /**
     * Trigger updating the user lists. This is only
     * supported for lists and not for searches (those are not persisted)
     * @param v
     */
    @Override
    public void reload(View v) {
        if (mode==0) {
            new SyncAllListsTask(this).execute();
        }
    }

    class SyncAllListsTask extends AsyncTask<Void,Object,Void> {

        Context context;
        String updating;

        SyncAllListsTask(Context context) {
            this.context = context;
            updating = context.getString(R.string.updating);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
//            setProgressBarVisibility(true);
            pg.setVisibility(ProgressBar.VISIBLE);

        }


        @Override
        protected Void doInBackground(Void... params) {

            for (Map.Entry<String,Integer> userList : userListsEntries) {

                publishProgress(userList.getKey());

                Paging paging = new Paging();
                paging.setCount(100);
                int listId = userList.getValue();
                long lastFetched = tdb.getLastRead(listId);
                if (lastFetched>0)
                    paging.setSinceId(lastFetched);
                MetaList<twitter4j.Status> list = th.getUserList(paging, listId, false);
                long newOnes = list.getNumOriginal();
                if (newOnes>0) {
                    long maxId = list.getList().get(0).getId();
                    tdb.updateOrInsertLastRead(listId,maxId);

                    publishProgress(userList.getKey(),newOnes);

                }
            }

            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            setProgressBarVisibility(false);
            pg.setVisibility(ProgressBar.INVISIBLE);
            titleTextBox.setText("");
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            super.onProgressUpdate(values);


            String list = (String) values[0];

            if (values.length==2) {
                Long num = (Long) values[1];
                Toast.makeText(context, list + ": " + num + " new", Toast.LENGTH_SHORT).show();
            } else { // len =1

                titleTextBox.setText(updating + " " + list + "...");
            }
            // Support Progress bar with a determinate value
//            int i = values[0];
//            int val = (i * 10000) / userListsEntries.size();
//            Log.d("SyAlLiTa","progress: " + val);
//            setProgress(val);
//            pg.setProgress(val);

            // Update the list entries somehow with a marker that they have new tweets
//                ListView listView = getListView();
//                for (int i = 0; i < listView.getCount(); i++) {
//                    String itemAtI = (String) listView.getItemAtPosition(i);
//                    if (itemAtI.equals(userList.getKey())) {
//                        adapter.remove(itemAtI);
//                        itemAtI = itemAtI + "(" + newOnes + ")";
//                        adapter.insert(itemAtI,i);
//                    }
//                }


        }
    }
}
