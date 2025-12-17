package com.example.uberfrontend.ui.drop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.uberfrontend.databinding.FragmentDropLocationBinding
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.Marker

class DropLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDropLocationBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleMap: GoogleMap
    private var dropMarker: Marker? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDropLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(binding.dropMapContainer.id) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val india = LatLng(28.6139, 77.2090)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(india, 12f))

        googleMap.setOnMapClickListener { latLng ->
            dropMarker?.remove()
            dropMarker = googleMap.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(latLng)
                    .title("Drop here")
            )

            binding.btnConfirmDrop.text = "Confirm: ${latLng.latitude}, ${latLng.longitude}"
        }

        binding.btnConfirmDrop.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}