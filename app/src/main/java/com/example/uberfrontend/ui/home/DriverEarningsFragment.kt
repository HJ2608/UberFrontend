package com.example.uberfrontend.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uberfrontend.R
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.DriverApi
import com.example.uberfrontend.data.session.SessionManager
import com.example.uberfrontend.databinding.FragmentDriverEarningsBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale

class DriverEarningsFragment : Fragment(R.layout.fragment_driver_earnings) {

    private var _binding: FragmentDriverEarningsBinding? = null
    private val binding get() = _binding!!

    private val adapter = EarningsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDriverEarningsBinding.bind(view)

        binding.rvTrips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrips.adapter = adapter

        binding.incToday.tvLabel.text = "Today"
        binding.incWeek.tvLabel.text = "This Week"
        binding.incMonth.tvLabel.text = "This Month"
        binding.incTotal.tvLabel.text = "Total"

        binding.btnRefresh.setOnClickListener { loadEarnings() }

        loadEarnings()
    }

    private fun loadEarnings() {
        val token = SessionManager.token

        if (token.isNullOrBlank() ) {
            Toast.makeText(requireContext(), "Missing token", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val api = ApiClient.create(DriverApi::class.java)
                val today = api.getTodayEarnings()
                val total = api.getTotalEarnings()
                val trips = api.getEarningTrips()

                binding.incToday.tvValue.text = formatMoney(today)
                binding.incTotal.tvValue.text = formatMoney(total)

                binding.incWeek.tvValue.text = formatMoney(0.0)
                binding.incMonth.tvValue.text = formatMoney(0.0)

                val tripsUi = trips.map { t ->
                    com.example.uberfrontend.data.model.DriverTripUi(
                        tripId = t.rideId,
                        title = "Ride #${t.rideId}",
                        subtitle = t.createdAt ?: "—",
                        amount = t.driverCut
                    )
                }
                adapter.submitList(tripsUi)


            } catch (e: HttpException) {
                Toast.makeText(requireContext(), "Failed: ${e.code()}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatMoney(value: Double): String {
        return String.format(Locale.US, "$%.2f", value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
