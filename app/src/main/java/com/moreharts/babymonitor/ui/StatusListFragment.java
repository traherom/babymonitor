package com.moreharts.babymonitor.ui;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.moreharts.babymonitor.R;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 * Activities containing this fragment MUST implement the Callbacks interface.
 */
public class StatusListFragment extends ListFragment implements AdapterView.OnItemClickListener {
    public static final String TAG = "StatusListFragment";

    /**
     * The fragment's ListView/GridView.
     */
    private ListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private ListAdapter mAdapter;

    // TODO: Rename and change types of parameters
    public static StatusListFragment newInstance(String param1, String param2) {
        StatusListFragment fragment = new StatusListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public StatusListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new LocalStatusAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_item, container, false);

        mListView = (ListView)view.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        return view;
    }

    @Override
    public void setListAdapter(ListAdapter a) {
        super.setListAdapter(a);

        // Setting the onitemclick listener doesn't work until the adapter is set
        mListView.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "Item " + position + " clicked");

        // Show appropriate dialog
        switch(position) {
            default:
                Log.d(TAG,"Ignoring click on item " + position);
        }
    }

    public void forceRefresh() {
        ((LocalStatusAdapter)getListAdapter()).forceRefresh();
    }

    /**
     * The default content for this Fragment has a TextView that is shown when
     * the list is empty. If you would like to change the text, call this method
     * to supply the text it should use.
     */
    public void setEmptyText(CharSequence emptyText) {
        View emptyView = mListView.getEmptyView();

        if (emptyText instanceof TextView) {
            ((TextView) emptyView).setText(emptyText);
        }
    }
}
