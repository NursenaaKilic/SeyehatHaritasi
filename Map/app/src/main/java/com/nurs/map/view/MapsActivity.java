package com.nurs.map.view;

import static androidx.room.Room.databaseBuilder;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;
import com.nurs.map.R;
import com.nurs.map.databinding.ActivityMapsBinding;
import com.nurs.map.model.Place;
import com.nurs.map.roomdb.PlaceDao;
import com.nurs.map.roomdb.PlaceDatabase;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLoadedCallback, GoogleMap.OnMapClickListener {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    Location currentLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationManager locationManager;
    ActivityResultLauncher<String> permissionLauncher;
    LocationListener locationListener;
    SharedPreferences sharedPreferences;
    boolean info;
    PlaceDatabase db;
    PlaceDao placeDao;
    Double selectedLatitude;
    Double selectedLongitude;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        registerLauncher();

       sharedPreferences = this.getSharedPreferences("com.nurs.map",MODE_PRIVATE);
       info = false;

       db = Room.databaseBuilder(MapsActivity.this,PlaceDatabase.class,"Places")
               //.allowMainThreadQueries();
               .build();
       placeDao = db.placeDao();

       selectedLatitude = 0.0;
       selectedLongitude = 0.0;
    }




    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapClickListener(this);

        binding.saveButton.setEnabled(false);


        //casting
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {

                info = sharedPreferences.getBoolean("info",false);

                if(!info){
                    LatLng userLocation = new LatLng(location.getLatitude(),location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation,15));
                    sharedPreferences.edit().putBoolean("info",true).apply();
                }

            }


        };


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
        {

            //request permission BU KADAN  biddi
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Snackbar.make(binding.getRoot(), "Permission needed for maps", Snackbar.LENGTH_INDEFINITE).setAction("Give Permission", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);

                    }
                }).show();
            } else  {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }

        }
        else  {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(lastLocation != null){
                LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation,15));
            }

            mMap.setMyLocationEnabled(true);

        }


    }



    private void registerLauncher() {
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                if (result) {
                    if (ContextCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                        Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if(lastLocation != null){
                            LatLng lastUserLocation = new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastUserLocation,15));
                        }

                    } else {
                        Toast.makeText(MapsActivity.this, "Permission needed!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }



    @Override
    public void onMapClick(@NonNull LatLng latLng) {
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng));

        selectedLatitude = latLng.latitude;
        selectedLongitude = latLng.longitude;

        binding.saveButton.setEnabled(true);

    }

    @Override
    public void onMapLoaded() {

    }

    public void save(View view){

        Place place = new Place(binding.placeNameText.getText().toString(),selectedLatitude,selectedLongitude);


        //placeDao.insert(place).subscribeOn(Schedulers.io()).subscribe());

        compositeDisposable.add(placeDao.insert(place)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(MapsActivity.this::handleResponce)
        );

    }

    private void handleResponce(){
        Intent intent = new Intent(MapsActivity.this,Main.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    public void delete(View view){
     /*
        compositeDisposable.add(placeDao.delete()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(MapsActivity.this::handleResponce)
        );
     */
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }
}