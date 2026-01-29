package com.example.uberfrontend.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.R
import com.example.uberfrontend.databinding.FragmentSignupBinding
import androidx.lifecycle.lifecycleScope
import com.example.uberfrontend.data.network.ApiClient
import com.example.uberfrontend.data.network.AuthApi
import com.example.uberfrontend.data.model.SignupRequestDto
import kotlinx.coroutines.launch
class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val TAG = "SignupFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSignup.setOnClickListener {
            val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
            val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
            val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString()?.trim().orEmpty()

            if (firstName.isEmpty()) {
                binding.tilFirstName.error = "First name is required"
                return@setOnClickListener
            } else {
                binding.tilFirstName.error = null
            }

            if (lastName.isEmpty()) {
                binding.tilLastName.error = "Last name is required"
                return@setOnClickListener
            } else {
                binding.tilLastName.error = null
            }

            if (phone.length < 8) {
                binding.tilPhone.error = "Enter a valid phone number"
                return@setOnClickListener
            } else {
                binding.tilPhone.error = null
            }

            if (email.isEmpty() || !email.contains("@")) {
                binding.tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            } else {
                binding.tilEmail.error = null
            }


            if (password.length < 6) {
                binding.tilPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            } else {
                binding.tilPassword.error = null
            }

            val req = SignupRequestDto(
                firstName = firstName,
                lastName = lastName,
                mobileNum = phone,
                email = email,
                password = password
            )

            lifecycleScope.launch {
                try {
                    val api = ApiClient.create(AuthApi::class.java)
                    val res = api.signup(req)

                    if (res.isSuccessful) {
                        val body = res.body()
                        if (body == null) {
                            Toast.makeText(
                                requireContext(),
                                "Empty response from server",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        Toast.makeText(
                            requireContext(),
                            "Signup successful: ${body.firstName}",
                            Toast.LENGTH_SHORT
                        ).show()

                        findNavController().navigate(R.id.action_signupFragment_to_loginFragment)

                    }else {
                            when (res.code()) {
                                409 -> Toast.makeText(
                                    requireContext(),
                                    "Mobile or email already registered",
                                    Toast.LENGTH_SHORT
                                ).show()

                                else -> Toast.makeText(
                                    requireContext(),
                                    "Signup failed: ${res.code()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error", e)
                    Toast.makeText(
                        requireContext(),
                        "Unexpected error: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
