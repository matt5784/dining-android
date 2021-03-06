package org.vandymobile.dining;

import java.util.ArrayList;

import org.vandymobile.dining.util.Locations;
import org.vandymobile.dining.util.Restaurant;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

/**
 * @author Matthew Lavin
 */

public class DiningMap extends MapActivity {

    AllOverlays mDiningOverlay;
    private MapController mMapViewController;
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    GeoPoint mPoint = null;
    private static Locations loc;
    //MyLocationOverlay myLocationOverlay;
    MapView myMap;
    com.google.android.maps.MyLocationOverlay MLO;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loc = Locations.getInstance(getApplicationContext());
        
        setContentView(R.layout.dining_map_large);
        //getActionBar().setDisplayHomeAsUpEnabled(true);
        myMap = (MapView) findViewById(R.id.mapview);
        myMap.setBuiltInZoomControls(true);
                
        GeoPoint _geoPoint = new GeoPoint(36143091, -86804699); //This is roughly the center of Vanderbilt
        mMapViewController = myMap.getController();
        mMapViewController.animateTo(_geoPoint);
        mMapViewController.setZoom(17); //center map on this point, zoomed to fit
        
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new MyLocationListener();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);//get current GPS location into a listener
 
        
        Location x = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (x == null){
            x = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (x == null){
            x = mLocationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        if (x != null){
            mPoint = new GeoPoint((int)(x.getLatitude()*1000000), 
                             (int)(x.getLongitude()*1000000));//current position
            //myLocationOverlay = new MyLocationOverlay();
            //myMap.getOverlays().add(myLocationOverlay); //this is an overlay which contains an image for our current location
            //This overlay is only drawn if we successfully retrieved the location of the user. 
        } else {
            Toast.makeText(getApplicationContext(), "Couldn't get location - defaulting", Toast.LENGTH_SHORT).show();
            mPoint = new GeoPoint(36143091, -86804699); //defaults to Vanderbilt if the current position cannot be determined
        }
        
        // creates the overlay containing markers for all dining locations
        // uses the database
        MLO = new com.google.android.maps.MyLocationOverlay(this,myMap);
        MLO.enableMyLocation(); 
        mDiningOverlay = new AllOverlays(this, myMap);
        myMap.getOverlays().add(mDiningOverlay);
        myMap.getOverlays().add(MLO);
    }
    
    public void homeClick(View v){
        Intent _int = new Intent(getApplicationContext(), DiningListView.class);
        startActivity(_int);
    }
    public void mapsClick(View v){
        // Already at map - do nothing
    }
    public void menuClick(View v){
        Intent intent = new Intent(getApplicationContext(), Menus.class);
        startActivity(intent);
    }
    public void happyClick(View v){
        //TODO implement this
    }

    @Override 
    public void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(mLocationListener);
    }
    @Override
    public void onResume(){
        super.onResume();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    public class MyLocationListener implements LocationListener{

        public void onLocationChanged(Location loc) {
            GeoPoint locPoint = new GeoPoint((int)(loc.getLatitude()*1000000),(int)(loc.getLongitude()*1000000));
            mPoint = locPoint;
            /*myLocationOverlay = new MyLocationOverlay();
            myMap.getOverlays().add(myLocationOverlay);*/
            //mMapViewController.animateTo(locPoint); //follow the user? Not sure if we want this to happen or not...
        }
        
        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub
        }

        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub
        }

        public void onStatusChanged(String provider,
            int status, Bundle extras) {
            // TODO Auto-generated method stub
        }
    } 

    /** 
     * AllOverlays: Adds overlays to the map for all locations in the database
     * This class is adapted from a class of the same name in the previous version of the app. The author of that class is Austin Conner.
     */
    public class AllOverlays extends ItemizedOverlay<OverlayItem> implements View.OnClickListener {
        
        private static final int NUM_FILTERS = 3;
        public static final int FILTER_CLOSED = 0;
        public static final int FILTER_PLAN = 1;
        public static final int FILTER_MONEY = 2;
        
        private int clickedPosition = -1;
        private DiningMap map;
        private MapView mapView;
        private RelativeLayout popup;
        //private ImageView icon;
        private TextView popupText;
        private TextView specialText;
        
        private ArrayList<OverlayItem> locationOverlay = new ArrayList<OverlayItem>();
        private boolean [][] show; // will show an item only if every entry in the column is true

        public AllOverlays(DiningMap map, MapView mapview) {

            super(boundCenterBottom(map.getResources().getDrawable(R.drawable.pushpin)));
            this.map = map;
            this.mapView = mapview;
            popup = (RelativeLayout)mapview.findViewById(R.map.popup);
            //icon = (ImageView)popup.findViewById(R.map.icon);
            popupText = (TextView)popup.findViewById(R.map.title);
            specialText = (TextView)popup.findViewById(R.map.specialText);
            
            popup.setOnClickListener(this);
            
            //ArrayList<Long> IDs = Restaurant.getIDs();
            show = new boolean [NUM_FILTERS][loc.mCount]; // only 1 possible criteria for showing now

            for (int i = 0; i < loc.mCount; i++) {
                Time now = new Time();
                now.setToNow();
                Restaurant cur = loc.mLocations[i];
                String status = DiningListView.isOpen(DiningListView.parseHours(cur.getHours()),now);
                OverlayItem overlayItem = new OverlayItem(cur.mLocation, cur.mName, status);
                /*if (Restaurant.offCampus(IDs.get(i)))
                    overlayItem.setMarker(boundCenterBottom(map.getResources().getDrawable(R.drawable.map_marker_n)));
                        // TODO get a better custom marker for off campus restaurants and/or make more custom markers for different 
                        // types or individual restaurants
                else overlayItem.setMarker(boundCenterBottom(map.getResources().getDrawable(R.drawable.map_marker_v)));*/
                overlayItem.setMarker(boundCenterBottom(map.getResources().getDrawable(R.drawable.pushpin)));
                locationOverlay.add(overlayItem);
                for (int j = 0; j < NUM_FILTERS; j++)
                    show[j][i] = true;
            } 
            populate();
        }

        @Override
        protected boolean onTap(int index) {
            if (clickedPosition == index) {
                clickedPosition = -1;
                popup.setVisibility(View.GONE);
                return true; //super.onTap(index);
            }
            clickedPosition = index;

            //icon.setImageResource(Restaurant.getIcon(Restaurant.getIDs().get(index)));
            popupText.setText(getItem(index).getTitle());
            specialText.setText(getItem(index).getSnippet());
            
            popup.setLayoutParams(new MapView.LayoutParams(MapView.LayoutParams.WRAP_CONTENT, MapView.LayoutParams.WRAP_CONTENT, 
                    getItem(index).getPoint(), 0, -getItem(index).getMarker(0).getIntrinsicHeight(), MapView.LayoutParams.BOTTOM_CENTER));
            popup.setVisibility(View.VISIBLE);
            
            mapView.getController().animateTo(getItem(index).getPoint());
            return true; //super.onTap(index);
        }
        
        private int lastI;
        private int lastIndex;
        @Override
        protected OverlayItem createItem(int i) {
            if (lastI == i - 1) {
                for (int j = lastIndex + 1; j < show[0].length; j++)
                    if (getShowItem(j)) {
                        lastI = i;
                        lastIndex = j;
                        return locationOverlay.get(j);
                    }
            } else {
                int num = -1;
                for (int j = 0; j < show[0].length; j++) {
                    if (getShowItem(j))
                        num++;
                    if (num == i) {
                        lastI = i;
                        lastIndex = j;
                        return locationOverlay.get(j);
                    }
                }
            }
            throw new RuntimeException("createItem error");
        }

        @Override
        public int size() {
            int size = 0;
            for (int i = 0; i < show[0].length; i++) 
                if (getShowItem(i))
                    size++;
            return size;
        }


        public void onClick(View v) {
            Intent toDetails = new Intent(map, LocationDetails.class);
            long id = (long)clickedPosition;
            toDetails.putExtra("id", id);//TODO this only opens the correct location when no settings are pressed
            map.startActivity(toDetails);
        }
        
        /**
         * getLocationOverlay: returns the list of OverlayItems which have been added to the map
         * @return: a list of OverlayItems
         */
        public ArrayList<OverlayItem> getLocationOverlay() {
            return locationOverlay;
        }
        
        public void setHideForFilter(boolean hide, int filter) {
            if (!hide) 
                for (int i = 0; i < show[0].length; i++)
                    setShowItem(i, filter, true);
            else {
                switch (filter) {
                case FILTER_CLOSED:
                    for (int i = 0; i < show[0].length; i++)
                        if (/*!Restaurant.getHours(Restaurant.getIDs().get(i)).isOpen()*/!loc.mLocations[i].isOpen())
                            setShowItem(i, filter, false);
                    break;
                case FILTER_PLAN:
                    for (int i = 0; i < show[0].length; i++)
                        if (/*!Restaurant.mealPlanAccepted(Restaurant.getIDs().get(i))*/!loc.mLocations[i].mMealPlan)
                            setShowItem(i, filter, false);
                    break;
                case FILTER_MONEY:
                    for (int i = 0; i < show[0].length; i++)
                        if (/*!Restaurant.mealMoneyAccepted(Restaurant.getIDs().get(i))*/!loc.mLocations[i].mMealMoney)
                            setShowItem(i, filter, false);
                    break;
                }
            }
        }
        
        /**
         * setShowItem: Sets whether a specific item is visible or not
         * @param i: the item id
         * @param filter: the filter which is currently applied
         * @param display: whether the item is visible
         */
        public void setShowItem(int i, int filter, boolean display) {
            show[filter][i] = display;
        }
        
        /**
         * getShowItem: finds whether an item should be visible or not
         * @param i: the id of the item
         * @return: an array which says whether the item should be shown or not for each filter
         */
        public boolean getShowItem(int i) {
            for (int j = 0; j<NUM_FILTERS; j++)
                if (!show[j][i])
                    return false;
            return true;
        }
        
        public void notifyDataSetChanged() {
            populate();
        }

    }
    
    // MENU FUNCTIONS
    
    public static final int MENU_SETTINGS = 0;
    public static final int MENU_CURRENT_LOC = 1;
   
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
            super.onCreateOptionsMenu(menu);
            menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, "Settings").
                            setIcon(getResources().getDrawable(android.R.drawable.ic_menu_preferences));
            menu.add(Menu.NONE, MENU_CURRENT_LOC, Menu.NONE, "My Location").
                            setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
            return true;
    }
   
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
            super.onOptionsItemSelected(item);
            switch (item.getItemId()) {
                case MENU_SETTINGS:
                    showDialog(DIALOG_SETTINGS); //This is deprecated, but the new way uses fragments, which requires extending the FragmentActivity class. 
                    //Since we are extending MapActivity, there is no good way to use fragments. 
                    return true;
                case MENU_CURRENT_LOC:
                    if (MLO.getMyLocation() != null) {
                        myMap.getController().animateTo(MLO.getMyLocation());
                    } else {
                        Toast.makeText(this, "Your location is temporarily unavailable", Toast.LENGTH_SHORT).show();
                    }
                    // TODO make getting location device type text, same as in Main
                    return true;
            }
            return true;
    }
   
    // DIALOG FUNCTIONS
   
    public static final int DIALOG_SETTINGS = 0;
   
    private static final boolean [] SETTINGS_DEFAULT = {false, false, false};
    private final boolean [] settingsChecked = SETTINGS_DEFAULT.clone();
   
    @Override
    protected Dialog onCreateDialog(int id) {
        super.onCreateDialog(id);
        switch (id) {
            case DIALOG_SETTINGS:
            default: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                CharSequence[] settings = { "Hide closed locations", "Hide no meal plan", "Hide no meal money" };

                builder.setMultiChoiceItems(settings, settingsChecked, new DialogInterface.OnMultiChoiceClickListener() {

                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        settingsChecked[which] = isChecked;
                            ((AlertDialog) dialog).getListView().setItemChecked(which, isChecked);
                    }
                });
                   
                builder.setNeutralButton("Done", new DialogInterface.OnClickListener() {
   
                    public void onClick(DialogInterface dialog, int which) {
                    DiningMap.this.updateSettings();
                    dialog.dismiss();
                    }
                });
                   
                builder.setNegativeButton("Set Defaults", new DialogInterface.OnClickListener() {           

                    public void onClick(DialogInterface dialog, int which) {
                        for (int i = 0; i<SETTINGS_DEFAULT.length; i++) {
                            settingsChecked[i]=SETTINGS_DEFAULT[i];
                        }
                        DiningMap.this.updateSettings();
                        dialog.dismiss();
                    }
                });

                builder.setTitle("Settings");

                return builder.create();
            }
        }
    }
   
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
            super.onPrepareDialog(id, dialog);
            switch (id) {
            case DIALOG_SETTINGS:
                    for (int i = 0; i < settingsChecked.length; i++) {
                            // TODO make this work correctly, (messes up if set Defaults is pressed
                            ((AlertDialog)dialog).getListView().setItemChecked(i, settingsChecked[i]);
                    }
            }
    }
   
    private void updateSettings() {
            mDiningOverlay.setHideForFilter(settingsChecked[0], AllOverlays.FILTER_CLOSED);
            mDiningOverlay.setHideForFilter(settingsChecked[1], AllOverlays.FILTER_PLAN);
            mDiningOverlay.setHideForFilter(settingsChecked[2], AllOverlays.FILTER_MONEY);
            mDiningOverlay.notifyDataSetChanged();
            myMap.invalidate();
    }    
}