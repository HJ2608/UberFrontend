package com.example.uberfrontend.ui.drop

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.data.model.PaymentRequestDto
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.RideApi
import com.example.uberfrontend.data.session.SessionManager
import com.example.uberfrontend.databinding.FragmentDriverPaymentBinding
import kotlinx.coroutines.launch

class DriverPaymentFragment : Fragment(R.layout.fragment_driver_payment) {

    private val TAG = "DriverPaymentFragment"
    private lateinit var binding: FragmentDriverPaymentBinding

    private val rideId: Int by lazy {
        requireArguments().getInt("rideId")
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentDriverPaymentBinding.bind(view)

        binding.btnMarkPaymentDone.setOnClickListener {
            markPaymentDone()
        }
    }
    private fun markPaymentDone() {
        lifecycleScope.launch {
            try {
                val token = SessionManager.token
                if (token.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Session expired", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val api = ApiClient.create(RideApi::class.java)
                val body = PaymentRequestDto(method = "CASH")

                val resp = api.markPaymentSuccess(
                    rideId = rideId,
                    body = body
                )

                if (resp.isSuccessful) {
                    Toast.makeText(requireContext(), "Payment marked done", Toast.LENGTH_SHORT).show()

                    val nav = findNavController()

                    nav.getBackStackEntry(R.id.driverHomeFragment)
                        .savedStateHandle["RIDE_FINISHED"] = true

                    nav.popBackStack(R.id.driverHomeFragment, false)

                } else {
                    val err = resp.errorBody()?.string()
                    Toast.makeText(requireContext(), err ?: "Payment failed", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "markPaymentDone failed: ${e::class.java.name} -> ${e.message}", e)
                Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }
}