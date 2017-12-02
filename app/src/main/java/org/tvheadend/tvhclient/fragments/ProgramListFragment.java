package org.tvheadend.tvhclient.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;

import org.tvheadend.tvhclient.Constants;
import org.tvheadend.tvhclient.DataStorage;
import org.tvheadend.tvhclient.DatabaseHelper;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.TVHClientApplication;
import org.tvheadend.tvhclient.adapter.ProgramListAdapter;
import org.tvheadend.tvhclient.htsp.HTSService;
import org.tvheadend.tvhclient.interfaces.ToolbarInterface;
import org.tvheadend.tvhclient.interfaces.FragmentControlInterface;
import org.tvheadend.tvhclient.interfaces.FragmentStatusInterface;
import org.tvheadend.tvhclient.interfaces.HTSListener;
import org.tvheadend.tvhclient.model.Channel;
import org.tvheadend.tvhclient.model.Connection;
import org.tvheadend.tvhclient.model.Profile;
import org.tvheadend.tvhclient.model.Program;
import org.tvheadend.tvhclient.model.Recording;
import org.tvheadend.tvhclient.utils.MenuUtils;
import org.tvheadend.tvhclient.utils.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProgramListFragment extends Fragment implements HTSListener, FragmentControlInterface {

    private final static String TAG = ProgramListFragment.class.getSimpleName();

    private Activity activity;
    private ToolbarInterface toolbarInterface;
    private FragmentStatusInterface fragmentStatusInterface;

    private ProgramListAdapter adapter;
    private ListView listView;
    private Channel channel;
    private boolean isDualPane = false;
    private long showProgramsFromTime;

    // Prevents loading more data on each scroll event. Only when scrolling has
    // stopped loading shall be allowed
    private boolean allowLoading = false;

    private TVHClientApplication app;
    private DataStorage dataStorage;
    private MenuUtils menuUtils;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        // If the view group does not exist, the fragment would not be shown. So
        // we can return anyway.
        if (container == null) {
            return null;
        }

        View v = inflater.inflate(R.layout.list_layout, container, false);
        listView = (ListView) v.findViewById(R.id.item_list);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = getActivity();
        app = TVHClientApplication.getInstance();
        dataStorage = DataStorage.getInstance();

        if (activity instanceof ToolbarInterface) {
            toolbarInterface = (ToolbarInterface) activity;
        }
        if (activity instanceof FragmentStatusInterface) {
            fragmentStatusInterface = (FragmentStatusInterface) activity;
        }

        Bundle bundle = getArguments();
        if (bundle != null) {
            channel = dataStorage.getChannel(bundle.getLong("channelId", 0));
            isDualPane = bundle.getBoolean("dual_pane", false);
            showProgramsFromTime = bundle.getLong("show_programs_from_time", new Date().getTime());
        }

        // If the channel is null exit
        if (channel == null) {
            activity.finish();
            return;
        }

        menuUtils = new MenuUtils(getActivity());

        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.onListItemSelected(position, adapter.getItem(position), TAG);
                }
            }
        });

        List<Program> list = new ArrayList<>();
        adapter = new ProgramListAdapter(activity, list);
        listView.setAdapter(adapter);

        setHasOptionsMenu(true);
        registerForContextMenu(listView);
    }

    /**
     * Activated the scroll listener to more programs can be loaded when the end
     * of the program list has been reached.
     */
    private void enableScrollListener() {
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // Enable loading when the user has scrolled pretty much to the end of the list
                if ((++firstVisibleItem + visibleItemCount) > totalItemCount) {
                    allowLoading = true;
                }
            }

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // If loading is allowed and the scrolling has stopped, load more data 
                if (scrollState == SCROLL_STATE_IDLE && allowLoading) {
                    allowLoading = false;
                    if (fragmentStatusInterface != null) {
                        fragmentStatusInterface.moreDataRequired(channel, TAG);
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        app.addListener(this);
        if (!dataStorage.isLoading()) {
            populateList();
        }
    }

    /**
     * Fills the adapter with all program that are part of the given channel
     */
    private void populateList() {
        // This is required because addAll is only available in API 11 and higher
        if (channel != null) {
            CopyOnWriteArrayList<Program> epg = new CopyOnWriteArrayList<>(channel.epg);

            int availableProgramCount = epg.size();
            boolean currentProgramFound = false;

            // Search through the EPG and find the first program that is currently running.
            // Also count how many programs are available without counting the ones in the past.
            for (Program p : epg) {
                if (p.start.getTime() >= showProgramsFromTime ||
                        p.stop.getTime() >= showProgramsFromTime) {
                    currentProgramFound = true;
                    adapter.add(p);
                } else {
                    availableProgramCount--;
                }
            }

            if (!currentProgramFound || availableProgramCount < Constants.PROGRAMS_VISIBLE_BEFORE_LOADING_MORE) {
                if (fragmentStatusInterface != null) {
                    fragmentStatusInterface.moreDataRequired(channel, TAG);
                }
            }
        }
        adapter.notifyDataSetChanged();

        // Inform the activity to show the currently visible number of the
        // programs and that the program list has been filled with data.
        if (toolbarInterface != null && channel != null) {
            toolbarInterface.setActionBarTitle(channel.name);
            String items = getResources().getQuantityString(R.plurals.programs, adapter.getCount(), adapter.getCount());
            toolbarInterface.setActionBarSubtitle(items);
            if (!isDualPane) {
                if (Utils.showChannelIcons(activity)) {
                    toolbarInterface.setActionBarIcon(channel.iconBitmap);
                } else {
                    toolbarInterface.setActionBarIcon(R.mipmap.ic_launcher);
                }
            }
        }
        if (fragmentStatusInterface != null) {
            fragmentStatusInterface.onListPopulated(TAG);
        }
        enableScrollListener();
    }

    @Override
    public void onPause() {
        super.onPause();
        app.removeListener(this);
        listView.setOnScrollListener(null);
    }

    @Override
    public void onDestroy() {
        fragmentStatusInterface = null;
        toolbarInterface = null;
        super.onDestroy();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!getUserVisibleHint()) {
            return false;
        }
        // Get the currently selected program from the list where the context
        // menu has been triggered
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        // Check for a valid adapter size and objects
        if (info == null || adapter == null || adapter.getCount() <= info.position) {
            return super.onContextItemSelected(item);
        }

        final Program program = adapter.getItem(info.position);
        if (program == null) {
            return super.onContextItemSelected(item);
        }

        // Check if the context menu call came from the list in this fragment
        // (needed for support for multiple fragments in one screen)
        if (getView() != null && info.targetView.getParent() != getView().findViewById(R.id.item_list)) {
            return super.onContextItemSelected(item);
        }

        switch (item.getItemId()) {
        case R.id.menu_search_imdb:
            menuUtils.handleMenuSearchWebSelection(program.title);
            return true;

        case R.id.menu_search_epg:
            menuUtils.handleMenuSearchEpgSelection(program.title);
            return true;

        case R.id.menu_record_remove:
            Recording rec = program.recording;
            if (rec != null) {
                if (rec.isRecording()) {
                    menuUtils.handleMenuStopRecordingSelection(rec.id, rec.title);
                } else if (rec.isScheduled()) {
                    menuUtils.handleMenuCancelRecordingSelection(rec.id, rec.title);
                } else {
                    menuUtils.handleMenuRemoveRecordingSelection(rec.id, rec.title);
                }
            }
            return true;

        case R.id.menu_record_once:
            menuUtils.handleMenuRecordSelection(program.id);
            return true;

        case R.id.menu_record_once_custom_profile:
            // Create the list of available recording profiles that the user can select from
            String[] dvrConfigList = new String[dataStorage.getDvrConfigs().size()];
            for (int i = 0; i < dataStorage.getDvrConfigs().size(); i++) {
                dvrConfigList[i] = dataStorage.getDvrConfigs().get(i).name;
            }

            // Get the selected recording profile to highlight the
            // correct item in the list of the selection dialog
            int dvrConfigNameValue = 0;
            DatabaseHelper databaseHelper = DatabaseHelper.getInstance(getActivity().getApplicationContext());
            final Connection conn = databaseHelper.getSelectedConnection();
            final Profile p = databaseHelper.getProfile(conn.recording_profile_id);
            if (p != null) {
                for (int i = 0; i < dvrConfigList.length; i++) {
                    if (dvrConfigList[i].equals(p.name)) {
                        dvrConfigNameValue = i;
                        break;
                    }
                }
            }

            // Create new variables because the dialog needs them as final
            final String[] dcList = dvrConfigList;

            // Create the dialog to show the available profiles
            new MaterialDialog.Builder(activity)
            .title(R.string.select_dvr_config)
            .items(dvrConfigList)
            .itemsCallbackSingleChoice(dvrConfigNameValue, new MaterialDialog.ListCallbackSingleChoice() {
                @Override
                public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                    // Pass over the
                    Intent intent = new Intent(activity, HTSService.class);
                    intent.setAction("addDvrEntry");
                    intent.putExtra("eventId", program.id);
                    intent.putExtra("channelId", program.channel.id);
                    intent.putExtra("configName", dcList[which]);
                    activity.startService(intent);
                    return true;
                }
            })
            .show();
            return true;

        case R.id.menu_record_series:
            menuUtils.handleMenuSeriesRecordSelection(program.title);
            return true;

        case R.id.menu_play:
            menuUtils.handleMenuPlaySelection(program.channel.id, -1);
            return true;

        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        activity.getMenuInflater().inflate(R.menu.program_context_menu, menu);

        // Get the currently selected program from the list where the context
        // menu has been triggered
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Program program = adapter.getItem(info.position);

        // Set the title of the context menu and show or hide 
        // the menu items depending on the program state
        if (program != null) {
            menu.setHeaderTitle(program.title);
            Utils.setProgramMenu(app, menu, program);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Hide the genre color menu in dual pane mode or if no genre colors shall be shown
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean showGenreColors = prefs.getBoolean("showGenreColorsProgramsPref", false);
        (menu.findItem(R.id.menu_genre_color_info_programs)).setVisible(!isDualPane && showGenreColors);
        (menu.findItem(R.id.menu_play)).setVisible(!isDualPane);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.program_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_play:
            // Open a new activity that starts playing the first program that is
            // currently transmitted over this channel
            menuUtils.handleMenuPlaySelection(channel.id, -1);
            return true;

        case R.id.menu_genre_color_info_programs:
            menuUtils.handleMenuGenreColorSelection();
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This method is part of the HTSListener interface. Whenever the HTSService
     * sends a new message the specified action will be executed here.
     */
    @Override
    public void onMessage(String action, final Object obj) {
        switch (action) {
            case Constants.ACTION_LOADING:
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        boolean loading = (Boolean) obj;
                        if (loading) {
                            adapter.clear();
                            adapter.notifyDataSetChanged();
                        } else {
                            populateList();
                        }
                    }
                });
                break;
            case "eventAdd":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        Program p = (Program) obj;
                        if (channel != null && p.channel.id == channel.id) {
                            adapter.add(p);
                            adapter.notifyDataSetChanged();
                            adapter.sort();
                            if (toolbarInterface != null) {
                                String items = getResources().getQuantityString(R.plurals.programs, adapter.getCount(), adapter.getCount());
                                toolbarInterface.setActionBarSubtitle(items);
                            }
                        }
                    }
                });
                break;
            case "eventDelete":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.remove((Program) obj);
                        adapter.notifyDataSetChanged();
                        if (toolbarInterface != null) {
                            String items = getResources().getQuantityString(R.plurals.programs, adapter.getCount(), adapter.getCount());
                            toolbarInterface.setActionBarSubtitle(items);
                        }
                    }
                });
                break;
            case "eventUpdate":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.update((Program) obj);
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
            case "dvrEntryUpdate":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        Recording rec = (Recording) obj;
                        for (Program p : adapter.getList()) {
                            if (rec == p.recording) {
                                adapter.update(p);
                                adapter.notifyDataSetChanged();
                                return;
                            }
                        }
                    }
                });
                break;
            case "dvrEntryAdd":
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
                break;
        }
    }

    @Override
    public void reloadData() {
        // NOP
    }
    @Override
    public void setSelection(int position, int index) {
        if (listView != null && listView.getCount() > position && position >= 0) {
            listView.setSelectionFromTop(position, index);
        }
    }

    @Override
    public void setInitialSelection(int position) {
        setSelection(position, 0);
        // Simulate a click in the list item to inform the activity
        if (adapter != null && adapter.getCount() > position) {
            Program p = adapter.getItem(position);
            if (fragmentStatusInterface != null) {
                fragmentStatusInterface.onListItemSelected(position, p, TAG);
            }
        }
    }

    @Override
    public Object getSelectedItem() {
        return channel;
    }

    @Override
    public int getItemCount() {
        return adapter.getCount();
    }
}
