package com.innodeed.jetosmpoly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.*

class MapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.INTERNET
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), 0)
        }

        setContent {

            MyApp()

            val ctx = applicationContext
            Configuration.getInstance()
                .load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            Configuration.getInstance().userAgentValue = "MapApp"

        }
    }
}

@Composable
fun MyApp() {
    MaterialTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier.fillMaxHeight()
            ) {

                var permissionState by remember { mutableStateOf(true) }
                ReqLocPermission(onPermissionResult = { isGranted ->
                    permissionState = isGranted
                })
                if (permissionState) {
                    MapViewWithPolyline()
                }
            }
        }
    }
}

@Composable
fun ReqLocPermission(onPermissionResult: (Boolean) -> Unit) {
    var isallow by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())
        { isGranted: Boolean ->
            onPermissionResult(isGranted)
            isallow = isGranted
        }

    LaunchedEffect(key1 = true) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (isallow) {

            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (isallow) {
                onPermissionResult(true)
            } else {
                onPermissionResult(true)
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        }
    }
}

@SuppressLint("MissingPermission")
@Preview
@Composable
fun MapViewWithPolyline() {
    val context = LocalContext.current
    val currentLocation = remember { mutableStateOf<GeoPoint?>(null) }
    val tappedLocations by remember { mutableStateOf(mutableListOf<GeoPoint>()) }
    val LogList: ArrayList<LocationData> = ArrayList()
    var lat by remember { mutableStateOf(0.0) }
    var lang by remember { mutableStateOf(0.0) }

    val mapView = MapView(context)
    val newMarker = Marker(mapView)
    MapContainer(
        mapView,
        context,
        newMarker,
        Polygon(),
        currentLocation,
        tappedLocations,
        LogList
    ) { osmMapView ->
        val fusedLocationClient: FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val locationPoint = GeoPoint(it.latitude, it.longitude)
                currentLocation.value = locationPoint
                lat = it.latitude
                lang = it.longitude

                osmMapView.setBuiltInZoomControls(true)
                osmMapView.setMultiTouchControls(true)
                osmMapView.controller.setZoom(18.0)
                osmMapView.controller.setCenter(currentLocation.value)
                // Refresh the map view to display the polyline
                osmMapView.invalidate()

            } ?: run {
                Toast.makeText(
                    context,
                    "Failed to retrieve current location",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val startPoint = GeoPoint(lat, lang)
        currentLocation.value = startPoint

    }
}

fun getUserAddress(startPoint: GeoPoint, context: Context): String {
    //val context = LocalContext.current
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = geocoder.getFromLocation(startPoint.latitude, startPoint.longitude, 1)
    return addresses!!.firstOrNull()?.getAddressLine(0) ?: "Unknown Address"
}

@Composable
fun MapContainer(
    mapView: MapView,
    context: Context,
    newMarker: Marker,
    polygon: Polygon,
    currentLocation: MutableState<GeoPoint?>,
    tappedLocations: MutableList<GeoPoint>,
    LogList: ArrayList<LocationData>,
    onMapReady: (MapView) -> Unit
) {
    AndroidView({ mapView }) { mapView ->
        onMapReady(mapView)
    }
    val mapEventsOverlay = rememberMapEventsOverlay {
        removeMarkerAndPolygon(mapView, newMarker, polygon)
        handleMapTap(mapView, currentLocation.value, it, context, tappedLocations, LogList)
    }
    mapView.overlays.add(0, mapEventsOverlay)

}

@Composable
fun rememberMapEventsOverlay(onMapClick: (GeoPoint) -> Unit): MapEventsOverlay {
    val mapEventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onMapClick(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        })
    }
    return mapEventsOverlay
}


fun handleMapTap(
    mapView: MapView,
    startPoint: GeoPoint?,
    geoPoint: GeoPoint,
    current: Context,
    tappedLocations: MutableList<GeoPoint>,
    LogList: ArrayList<LocationData>
) {

//    tappedLocations.add(geoPoint)
    tappedLocations.add(GeoPoint(geoPoint.latitude, geoPoint.longitude))

    LogList.add(LocationData(geoPoint.latitude.toString(), geoPoint.longitude.toString()))

    val marker = Marker(mapView)
    marker.position = geoPoint
    marker.snippet = "end"
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    marker.title = getUserAddress(geoPoint, current)

    marker.setOnMarkerClickListener { marker, mapView ->
        Log.d("TAP_LOCATION", LogList.toString())
        true // Return true to consume the event
    }

    // Add the marker to the map
    mapView.overlays.add(marker)

    if (tappedLocations.size >= 3) {
//        drawPolygon(mapView, tappedLocations)         // Polygon
        drawPolyline_new(mapView, tappedLocations)      // Polyline
    } else if (tappedLocations.size >= 2) {
        drawNewPolyline(
            mapView,
            tappedLocations[tappedLocations.size - 2],
            tappedLocations[tappedLocations.size - 1]
        )  // Polyline
    }
    mapView.invalidate()
}

fun drawPolygon(mapView: MapView, points: List<GeoPoint>) {
    val polygon = org.osmdroid.views.overlay.Polygon().apply {
        points.forEach { addPoint(it) }
    }
    polygon.getFillPaint().setColor(Color.BLUE);
    mapView.overlays.removeAt(mapView.overlays.size - 2)
    mapView.overlays.add(polygon)
    mapView.invalidate()
}


fun drawPolyline_new(mapView: MapView, points: List<GeoPoint>) {
    val polyline = org.osmdroid.views.overlay.Polyline().apply {
        points.forEach { addPoint(it) }
    }
    mapView.overlays.removeAt(mapView.overlays.size - 2)
    mapView.overlays.add(polyline)
    mapView.invalidate()
}

fun drawNewPolyline(mapView: MapView, startPoint: GeoPoint, endPoint: GeoPoint) {
    // Draw polyline
    val polyline = org.osmdroid.views.overlay.Polyline().apply {
        addPoint(startPoint)
        addPoint(endPoint)
    }
    mapView.overlays.add(polyline)
    mapView.invalidate()
}

fun calculatePolygonArea(vertices: List<GeoPoint>): Double {
    var area = 0.0
    val numPoints = vertices.size
    for (i in 0 until numPoints) {
        val j = (i + 1) % numPoints
        val vertexI = vertices[i]
        val vertexJ = vertices[j]
        area += vertexI.longitude * vertexJ.latitude
        area -= vertexI.latitude * vertexJ.longitude
    }
    area /= 2.0
    return Math.abs(area)
}

fun removeMarkerAndPolygon(mapView: MapView, marker: Marker, polygon: Polygon) {
    marker.remove(mapView)
    mapView.overlays.remove(polygon)
    mapView.invalidate()
}