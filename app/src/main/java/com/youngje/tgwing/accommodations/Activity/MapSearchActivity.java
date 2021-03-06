package com.youngje.tgwing.accommodations.Activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.util.Pair;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.youngje.tgwing.accommodations.Data.DataFormat;
import com.youngje.tgwing.accommodations.Data.DaumDataProcessor;
import com.youngje.tgwing.accommodations.Data.SeoulDataProcessor;
import com.youngje.tgwing.accommodations.Marker;
import com.youngje.tgwing.accommodations.R;
import com.youngje.tgwing.accommodations.User;
import com.youngje.tgwing.accommodations.Util.HttpHandler;
import com.youngje.tgwing.accommodations.Util.LocationUtil;

import net.daum.android.map.openapi.search.OnFinishSearchListener;
import net.daum.android.map.openapi.search.Searcher;
import net.daum.mf.map.api.CalloutBalloonAdapter;
import net.daum.mf.map.api.CameraUpdateFactory;
import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapPointBounds;
import net.daum.mf.map.api.MapView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.youngje.tgwing.accommodations.R.string.daum_api_key;
import net.daum.android.map.openapi.search.Item;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MapSearchActivity extends AppCompatActivity implements MapView.MapViewEventListener, MapView.POIItemEventListener, MapView.CurrentLocationEventListener, NavigationView.OnNavigationItemSelectedListener {

    private ImageView btnMore;
    private View layoutMore;
    private EditText mSearchView;
    private HashMap<Integer, Marker> mTagItemMap = new HashMap<Integer, Marker>();
    private MapView mMapView;
    private Location curlocate;
    private User curUser;

    private DrawerLayout mDrawer;
    private NavigationView mNavView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map_search);
        curlocate = LocationUtil.curlocation;
        curUser = User.getMyInstance();

        String createUrl;
        createUrl = DataFormat.createSeoulOpenAPIRequestURL(DataFormat.DATATYPE.WIFI, curlocate.getLatitude(), curlocate.getLongitude());
        HttpHandler httpHandler = new HttpHandler();


        try {

            String result = httpHandler.execute(createUrl).get();
            // TODO: 2016. 10. 15. null값일때 예외처리 해야됨
            if(result != null)
                Log.i("temp3", result);

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }



        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference();
        myRef.child("currentUser").child(curUser.getUserId()).setValue(curUser);




        btnMore = (ImageView) findViewById(R.id.activity_main_btn_more);
        layoutMore = (View) findViewById(R.id.activity_main_btn_category);
        layoutMore.bringToFront();
        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (layoutMore.isShown()) {
                    layoutMore.setVisibility(View.GONE);
                } else {
                    layoutMore.setVisibility(View.VISIBLE);
                }
            }
        });

        mMapView = (MapView)findViewById(R.id.map_view);
        mMapView.setDaumMapApiKey(getString(daum_api_key));
        mMapView.setMapViewEventListener(this);
        mMapView.setPOIItemEventListener(this);
        mMapView.setCalloutBalloonAdapter(new CustomCalloutBalloonAdapter());
        addSearch();

        ///////////////////////////////////drawer 부분입니다.
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mNavView = (NavigationView) findViewById(R.id.nav_view);
        mNavView.setNavigationItemSelectedListener(this);
    }
    void onDrawer(View view){
        mDrawer.openDrawer(mNavView);
        hideSoftKeyboard();
    }
    public void addSearch(){

        mSearchView = (EditText) findViewById(R.id.activity_main_searchbar); // 검색창
        mSearchView.setOnEditorActionListener(new EditText.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    String query = mSearchView.getText().toString();
                    mSearchView.setText(null);
                    if (query == null || query.length() == 0) {
                        showToast("검색어를 입력하세요.");
                        return false;
                    }
                    mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
                    hideSoftKeyboard(); // 키보드 숨김
                    MapPoint.GeoCoordinate geoCoordinate = mMapView.getMapCenterPoint().getMapPointGeoCoord();
                    double latitude = geoCoordinate.latitude; // 위도
                    double longitude = geoCoordinate.longitude; // 경도
                    int radius = 1000; // 중심 좌표부터의 반경거리. 특정 지역을 중심으로 검색하려고 할 경우 사용. meter 단위 (0 ~ 10000)
                    int page = 1; // 페이지 번호 (1 ~ 3). 한페이지에 15개
                    String apikey = getString(R.string.daum_api_key);

                    HttpHandler httpHandler = new HttpHandler();
                    String createUrl = null;
                    createUrl = DataFormat.createDaumKeywordRequestURL(query, curUser.getLat(), curUser.getLon(),radius, page,"",apikey);
                    try{
                        String HTTPResult = httpHandler.execute(createUrl).get();
                        DaumDataProcessor DDP = new DaumDataProcessor();
                        List<Marker> markerList = DDP.load(HTTPResult, null);

                        mMapView.removeAllPOIItems();
                        showResult(markerList);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    /*
                    Searcher searcher = new Searcher(); // net.daum.android.map.openapi.search.Searcher
                    searcher.searchKeyword(getApplicationContext(), query, latitude, longitude, radius, page, apikey, new OnFinishSearchListener() {
                        @Override
                        public void onSuccess(List<Item> itemList) {
                            mMapView.removeAllPOIItems(); // 기존 검색 결과 삭제
                            showResult(itemList); // 검색 결과 보여줌
                        }

                        @Override
                        public void onFail() {
                            showToast("API_KEY의 제한 트래픽이 초과되었습니다.");
                        }

                    });
                            */

                }

                return true;

            }
        });

    }
    public void categorySearch(DataFormat.DATATYPE dataFormat){
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);

        hideSoftKeyboard(); // 키보드 숨김
        MapPoint.GeoCoordinate geoCoordinate = mMapView.getMapCenterPoint().getMapPointGeoCoord();
        double latitude = geoCoordinate.latitude; // 위도
        double longitude = geoCoordinate.longitude; // 경도
        int radius = 1000; // 중심 좌표부터의 반경거리. 특정 지역을 중심으로 검색하려고 할 경우 사용. meter 단위 (0 ~ 10000)
        int page = 1; // 페이지 번호 (1 ~ 3). 한페이지에 15개
        String apikey = getString(R.string.daum_api_key);
        /*
        Searcher searcher = new Searcher(); // net.daum.android.map.openapi.search.Searcher
        searcher.searchCategory(getApplicationContext(), categoryCode, latitude, longitude, radius, page, apikey, new OnFinishSearchListener() {
            @Override
            public void onSuccess(List<Marker> markerList) {
                mMapView.removeAllPOIItems(); // 기존 검색 결과 삭제
                showResult(itemList); // 검색 결과 보여줌
            }

            @Override
            public void onFail() {
                showToast("API_KEY의 제한 트래픽이 초과되었습니다.");
            }
        });
        */
        if(dataFormat.equals(DataFormat.DATATYPE.WIFI) || dataFormat.equals(DataFormat.DATATYPE.TOILET)) {
            HttpHandler httpHandler = new HttpHandler();
            String createUrl = null;
            createUrl = DataFormat.createSeoulOpenAPIRequestURL(dataFormat, curUser.getLat(), curUser.getLon());
            try {
                String HTTPResult = httpHandler.execute(createUrl).get();
                SeoulDataProcessor SDP = new SeoulDataProcessor();
                List<Marker> markerList = SDP.load(HTTPResult, dataFormat);

                mMapView.removeAllPOIItems();
                showResult(markerList);
                Log.i("temptemptemp", HTTPResult);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                temp();
            } catch (ExecutionException | InterruptedException | JSONException e) {
                e.printStackTrace();
            }
        }else{
            Toast.makeText(this,dataFormat.toString(),Toast.LENGTH_SHORT).show();

            HttpHandler httpHandler = new HttpHandler();
            String createUrl = null;
            createUrl = DataFormat.createDaumCategoryRequestURL(dataFormat, curUser.getLat(), curUser.getLon(),radius, page,"",apikey);
            try{
                String HTTPResult = httpHandler.execute(createUrl).get();
                DaumDataProcessor DDP = new DaumDataProcessor();
                List<Marker> markerList = DDP.load(HTTPResult, dataFormat);

                mMapView.removeAllPOIItems();
                showResult(markerList);

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }



    }

    public void categoryClicked(View v) throws ExecutionException, InterruptedException {
        DataFormat.DATATYPE datatype = null;
        switch (v.getId()){
            case R.id.landmark: datatype = DataFormat.DATATYPE.AT4 ; break;
            case R.id.restroom: datatype = DataFormat.DATATYPE.TOILET; break;
            case R.id.wifizone: datatype = DataFormat.DATATYPE.WIFI; break;
            case R.id.bank: datatype = DataFormat.DATATYPE.BANK; break;
            case R.id.market: datatype = DataFormat.DATATYPE.CONVINEIENCE;break;
            case R.id.restaurant: datatype = DataFormat.DATATYPE.FOOD; break;
            case R.id.hotel: datatype = DataFormat.DATATYPE.MOTEL; break;
            case R.id.cafe: datatype = DataFormat.DATATYPE.CAFE; break;
            case R.id.pharmacy: datatype = DataFormat.DATATYPE.PHARMACY; break;
            case R.id.train: datatype = DataFormat.DATATYPE.SUBWAY; break;
            default: datatype = null; break;
        }


        // TODO: 2016. 10. 15. 합쳐야된다.
       // if(datatype != null) {
       //     HttpHandler httpHandler = new HttpHandler();
        //     String createUrl = null;
        //     DataFormat.DATATYPE dataFormat = DataFormat.DATATYPE.WIFI;
        //     createUrl = DataFormat.createSeoulOpenAPIRequestURL(dataFormat, curlocate.getLatitude(), curlocate.getLongitude());
        //     String HTTPResult = httpHandler.execute(createUrl).get();
//
       //     // TODO: 2016. 10. 15. parsing 하는거 만들어야됨
       // }

         categorySearch(datatype);


    }
    /** category codes
     MT1 대형마트
     CS2 편의점
     PS3 어린이집, 유치원
     SC4 학교
     AC5 학원
     PK6 주차장
     OL7 주유소, 충전소
     SW8 지하철역
     BK9 은행
     CT1 문화시설
     AG2 중개업소
     PO3 공공기관
     AT4 관광명소
     AD5 숙박
     FD6 음식점
     CE7 카페
     HP8 병원
     PM9 약국
     */
    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MapSearchActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        mDrawer.closeDrawer(mNavView);

        switch(id){
            case R.id.nav_home:
                Toast.makeText(this,"nav_home",Toast.LENGTH_SHORT).show(); break;
            case R.id.nav_attend:
                Toast.makeText(this,"nav_attend",Toast.LENGTH_SHORT).show(); break;
            case R.id.nav_attendlist:
                Toast.makeText(this,"nav_attendlist",Toast.LENGTH_SHORT).show();  break;
            case R.id.nav_info:
                Toast.makeText(this,"nav_info",Toast.LENGTH_SHORT).show(); break;
            case R.id.nav_logout:
                Toast.makeText(this,"nav_logout",Toast.LENGTH_SHORT).show(); break;
        }
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }


    class CustomCalloutBalloonAdapter implements CalloutBalloonAdapter {

        private final View mCalloutBalloon;

        public CustomCalloutBalloonAdapter() {
            mCalloutBalloon = getLayoutInflater().inflate(R.layout.custom_callout_balloon, null);
        }

        @Override
        public View getCalloutBalloon(MapPOIItem poiItem) {
            if (poiItem == null) return null;
            Marker marker = mTagItemMap.get(poiItem.getTag());
            if (marker == null) return null;
            ImageView imageViewBadge = (ImageView) mCalloutBalloon.findViewById(R.id.badge);
            TextView textViewTitle = (TextView) mCalloutBalloon.findViewById(R.id.title);
            textViewTitle.setText(marker.getTitle());
            TextView textViewDesc = (TextView) mCalloutBalloon.findViewById(R.id.desc);
            //textViewDesc.setText(item.address);
            imageViewBadge.setImageDrawable(createDrawableFromUrl(marker.getImageUrl()));
            return mCalloutBalloon;
        }

        @Override
        public View getPressedCalloutBalloon(MapPOIItem poiItem) {
            return null;
        }

    }
    protected void hideSoftKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    private Drawable createDrawableFromUrl(String url) {
        try {
            InputStream is = (InputStream) this.fetch(url);
            Drawable d = Drawable.createFromStream(is, "src");
            return d;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private void showResult(List<Marker> markerList) {
        MapPointBounds mapPointBounds = new MapPointBounds();

        for (int i = 0; i < markerList.size(); i++) {
            Marker marker = markerList.get(i);

            MapPOIItem poiItem = new MapPOIItem();
            poiItem.setItemName(marker.getTitle());
            poiItem.setTag(i);
            MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(marker.getLat(), marker.getLon());
            poiItem.setMapPoint(mapPoint);
            mapPointBounds.add(mapPoint);
            poiItem.setMarkerType(MapPOIItem.MarkerType.CustomImage);
            poiItem.setCustomImageResourceId(R.drawable.map_pin_blue);
            poiItem.setSelectedMarkerType(MapPOIItem.MarkerType.CustomImage);
            poiItem.setCustomSelectedImageResourceId(R.drawable.map_pin_red);
            poiItem.setCustomImageAutoscale(false);
            poiItem.setCustomImageAnchor(0.5f, 1.0f);

            mMapView.addPOIItem(poiItem);
            mTagItemMap.put(poiItem.getTag(), marker);
        }
        MapPoint.GeoCoordinate geoCoordinate = mMapView.getMapCenterPoint().getMapPointGeoCoord();
        double latitude = geoCoordinate.latitude; // 위도
        double longitude = geoCoordinate.longitude; // 경도
        mMapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(latitude, longitude), 3, true);

        MapPOIItem[] poiItems = mMapView.getPOIItems();
        if (poiItems.length > 0) {
            //mMapView.selectPOIItem(poiItems[0], false);
        }
    }

    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float v) {

    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {

    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

    }

    @Override
    public void onMapViewInitialized(MapView mapView) {
        mMapView.setCurrentLocationEventListener(this);
        mMapView.setShowCurrentLocationMarker(true);
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);


    }

    private Object fetch(String address) throws MalformedURLException,IOException {
        URL url = new URL(address);
        Object content = url.getContent();
        return content;
    }
    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {

    }

    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {

    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {

    }

    @Override
    public void onBackPressed() {
        int backCount = getSupportFragmentManager().getBackStackEntryCount();
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            if(backCount > 2) {
                super.onBackPressed();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {
        Marker marker = mTagItemMap.get(mapPOIItem.getTag());
        StringBuilder sb = new StringBuilder();
        sb.append("title=").append(marker.getTitle()).append("\n");
        sb.append("imageUrl=").append(marker.getImageUrl()).append("\n");
        //sb.append("address=").append(item.address).append("\n");
        //sb.append("newAddress=").append(item.newAddress).append("\n");
        //sb.append("zipcode=").append(item.zipcode).append("\n");
        //sb.append("phone=").append(item.phone).append("\n");
        //sb.append("category=").append(item.category).append("\n");
        sb.append("longitude=").append(marker.getLon()).append("\n");
        sb.append("latitude=").append(marker.getLat()).append("\n");
        sb.append("distance=").append(marker.getDistance()).append("\n");
        //sb.append("direction=").append(item.direction).append("\n");
        Toast.makeText(this, sb.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {

    }

    public ArrayList<String> temp() throws ExecutionException, InterruptedException, JSONException {
        HttpHandler httpHandler = new HttpHandler();
        String requestUrl = DataFormat.createGetAroungRequestURL(curUser.getLat(),curUser.getLon());
        String result = httpHandler.execute(requestUrl).get();
        JSONObject root = new JSONObject(result);

        ArrayList<String> userIdList = new ArrayList<>();

        String jsonArr = "[";
        Iterator iterator = root.keys();
        while(iterator.hasNext()) {
            String key = (String)iterator.next();
            JSONObject data = root.getJSONObject(key);
            jsonArr+=data.toString();
            jsonArr+=",";
        }
        jsonArr = jsonArr.substring(0, jsonArr.length()-1)+"]";

        JSONArray jsonArray = new JSONArray(jsonArr);
        for(int i=0 ; i<jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            userIdList.add(jsonObject.getString("userId"));
        }

        Log.i("temptemp",userIdList.toString());
        return userIdList;
    }
}
