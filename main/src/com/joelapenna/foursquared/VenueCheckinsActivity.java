/**
 * Copyright 2009 Joe LaPenna
 */

package com.joelapenna.foursquared;

import com.joelapenna.foursquare.Foursquare;
import com.joelapenna.foursquare.types.Checkin;
import com.joelapenna.foursquare.types.Group;
import com.joelapenna.foursquare.types.Mayor;
import com.joelapenna.foursquare.types.User;
import com.joelapenna.foursquare.types.Venue;
import com.joelapenna.foursquared.app.LoadableListActivity;
import com.joelapenna.foursquared.util.Comparators;
import com.joelapenna.foursquared.util.RemoteResourceManager;
import com.joelapenna.foursquared.util.StringFormatters;
import com.joelapenna.foursquared.widget.CheckinListAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import java.io.IOException;
import java.util.Collections;
import java.util.Observable;
import java.util.Observer;

/**
 * @author Joe LaPenna (joe@joelapenna.com)
 */
public class VenueCheckinsActivity extends LoadableListActivity {
    public static final String TAG = "VenueCheckinsActivity";
    public static final boolean DEBUG = FoursquaredSettings.DEBUG;

    private Observer mParentDataObserver = new ParentDataObserver();
    private CheckinListAdapter mListAdapter;
    private View mMayorLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initListViewAdapter();

        VenueActivity parent = (VenueActivity)getParent();

        if (parent.venueObservable.getVenue() != null) {
            mParentDataObserver.update(parent.venueObservable, parent.venueObservable.getVenue());
        } else {
            ((VenueActivity)getParent()).venueObservable.addObserver(mParentDataObserver);
        }
    }

    @Override
    public int getNoSearchResultsStringId() {
        return R.string.no_checkins_be_the_first;
    }

    private void initListViewAdapter() {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        mMayorLayout = inflater.inflate(R.layout.mayor, null);
        getListView().addHeaderView(mMayorLayout);

        TextView recentCheckinsHeader = (TextView)inflater.inflate(R.layout.list_header, null);
        recentCheckinsHeader.setText("Recent Checkins");
        getListView().addHeaderView(recentCheckinsHeader);

        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Checkin checkin = (Checkin)parent.getAdapter().getItem(position);
                if (checkin != null) {
                    startItemActivity(checkin.getUser());
                }
            }
        });

        mListAdapter = new HeaderAwareCheckinListAdapter(this, //
                ((Foursquared)getApplication()).getRemoteResourceManager());

        setListAdapter(mListAdapter);
    }

    private void putCheckinsInAdapter(Group<Checkin> checkins) {
        setEmptyView();
        mListAdapter.setGroup(checkins);
    }

    private void ensureMayor(final Venue venue) {
        if (DEBUG) Log.d(TAG, "Setting mayor.");

        if (venue.getStats() == null || venue.getStats().getMayor() == null) {
            getListView().removeHeaderView(mMayorLayout);
            return;
        } else {
            mMayorLayout.setVisibility(ViewGroup.VISIBLE);
        }

        final Mayor mayor = venue.getStats().getMayor();

        mMayorLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(VenueCheckinsActivity.this, UserActivity.class);
                intent.putExtra(UserActivity.EXTRA_USER, mayor.getUser().getId());
                startActivity(intent);
            }
        });

        ((TextView)findViewById(R.id.mayorName)).setText(StringFormatters
                .getUserAbbreviatedName(mayor.getUser()));
        ((TextView)findViewById(R.id.mayorCheckinCount)).setText( //
                mayor.getCount() + " Checkins");

        final ImageView photo = (ImageView)findViewById(R.id.mayorPhoto);
        final RemoteResourceManager rrm = ((Foursquared)getApplication())
                .getRemoteResourceManager();
        final Uri photoUri = Uri.parse(mayor.getUser().getPhoto());

        try {
            Bitmap bitmap = BitmapFactory.decodeStream(rrm.getInputStream(photoUri));
            photo.setImageBitmap(bitmap);
        } catch (IOException e) {
            if (DEBUG) Log.d(TAG, "photo not already retrieved, requesting: " + photoUri);
            rrm.addObserver(new RemoteResourceManager.ResourceRequestObserver(photoUri) {
                @Override
                public void requestReceived(Observable observable, Uri uri) {
                    if (DEBUG) Log.d(TAG, "Received mayor photo: " + uri);
                    observable.deleteObserver(this);
                    updateMayorPhoto(photo, uri, mayor);
                }
            });
            rrm.request(photoUri);
        }
    }

    private void startItemActivity(User user) {
        if (DEBUG) Log.d(TAG, "firing venue activity for venue");
        Intent intent = new Intent(VenueCheckinsActivity.this, UserActivity.class);
        intent.putExtra(UserActivity.EXTRA_USER, user.getId());
        startActivity(intent);
    }

    private void updateMayorPhoto(final ImageView photo, final Uri uri, final Mayor mayor) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (DEBUG) Log.d(TAG, "Loading mayor photo: " + uri);
                    RemoteResourceManager rrm = ((Foursquared)getApplication())
                            .getRemoteResourceManager();
                    Bitmap bitmap = BitmapFactory.decodeStream(rrm.getInputStream(uri));
                    photo.setImageBitmap(bitmap);
                    if (DEBUG) Log.d(TAG, "Loaded mayor photo: " + uri);
                } catch (IOException e) {
                    if (DEBUG) Log.d(TAG, "Unable to load mayor photo: " + uri);
                    if (Foursquare.MALE.equals(mayor.getUser().getGender())) {
                        photo.setImageResource(R.drawable.blank_boy);
                    } else {
                        photo.setImageResource(R.drawable.blank_girl);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Ummm............", e);
                }
            }
        });
    }

    private final class ParentDataObserver implements Observer {
        private boolean observed = false;

        @SuppressWarnings("unchecked")
        @Override
        public void update(Observable observable, Object data) {
            if (DEBUG) Log.d(TAG, "Received update from: " + observable.toString());
            VenueActivity parent = (VenueActivity)getParent();
            Venue venue = parent.venueObservable.getVenue();

            // For each checkin, lets get the user because that is what we're concerned about here.
            Group<Checkin> checkins = venue.getCheckins();
            Collections.sort(checkins, Comparators.getCheckinRecencyComparator());

            if (!observed && venue != null && checkins != null) {
                observed = true;
                ensureMayor(venue);
                putCheckinsInAdapter(checkins);
            }
        }
    }

    private class HeaderAwareCheckinListAdapter extends CheckinListAdapter {
        public HeaderAwareCheckinListAdapter(Context context, RemoteResourceManager rrm) {
            super(context, rrm);
        }

        @Override
        public boolean isEmpty() {
            // XXX This is such a hack.
            // Only state you're empty if there are no headers, this allows headers to be shown even
            // if there are no checkins at this venue.
            return super.isEmpty() && getListView().getHeaderViewsCount() < 2;
        }
    }
}
