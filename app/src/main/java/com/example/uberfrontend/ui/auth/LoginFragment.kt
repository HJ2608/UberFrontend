package com.example.uberfrontend.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.uberfrontend.databinding.FragmentLoginBinding
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.example.uberfrontend.network.ApiClient
import com.example.uberfrontend.network.AuthApi
import com.example.uberfrontend.network.dto.LoginRequestDto
import com.example.uberfrontend.session.SessionManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnContinue.setOnClickListener {
            val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString()?.trim().orEmpty()

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Enter phone & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val req = LoginRequestDto(
                mobileNum = phone,
                password = password
            )

            lifecycleScope.launch {
                try {
                    val api = ApiClient.create(AuthApi::class.java)
                    val res = api.login(req)

                    SessionManager.saveLogin(
                        requireContext(),
                        jwt = res.accessToken,
                        userId = res.userId,
                        firstName = res.firstName,
                        lastName = res.lastName,
                        mobile = res.mobileNum,
                        email = res.email
                    )

                    Toast.makeText(requireContext(), "Welcome ${res.firstName}", Toast.LENGTH_SHORT).show()

                    findNavController().navigate(
                        com.example.uberfrontend.R.id.action_loginFragment_to_homeFragment
                    )

                } catch (e: HttpException) {
                    val msg = if (e.code() == 401) "Invalid credentials"
                    else "Login failed: ${e.code()}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.tvGoToSignup.setOnClickListener {
            findNavController().navigate(
                com.example.uberfrontend.R.id.action_loginFragment_to_signupFragment
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}