package com.example.individualproject

import AuthApiService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch


sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val serverError: Boolean = false) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
}

class AuthViewModel<T> : ViewModel() {

    private val apiService: AuthApiService = RetrofitClient.instance

    private val _loginResult = MutableLiveData<AuthResult<LoginResponse>>()
    val loginResult: LiveData<AuthResult<LoginResponse>> = _loginResult

    private val _registrationResult = MutableLiveData<AuthResult<GenericAuthResponse>>()
    val registrationResult: LiveData<AuthResult<GenericAuthResponse>> = _registrationResult

    private val _forgotPasswordResult = MutableLiveData<AuthResult<GenericAuthResponse>>()
    val forgotPasswordResult: LiveData<AuthResult<GenericAuthResponse>> = _forgotPasswordResult

    fun login(email: String, password: String) {
        _loginResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                val requestBody = mapOf("email" to email, "password" to password)
                val response = apiService.loginUser(requestBody)
                if (response.isSuccessful) {
                    response.body()?.let {
                        if (!it.error) {
                            _loginResult.postValue(AuthResult.Success(it))

                            TokenManager.saveToken(it.token ?: "")
                        } else {
                            _loginResult.postValue(AuthResult.Error(it.message, serverError = true))
                        }
                    } ?: _loginResult.postValue(AuthResult.Error("Empty response body"))
                } else {

                    val errorMsg = response.errorBody()?.string() ?: "Login failed: ${response.code()}"
                    _loginResult.postValue(AuthResult.Error(errorMsg, serverError = true))
                }
            } catch (e: Exception) {
                _loginResult.postValue(AuthResult.Error("Network error or exception: ${e.message}"))
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        _registrationResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                val requestBody = mapOf("name" to name, "email" to email, "password" to password)
                val response = apiService.registerUser(requestBody)
                if (response.isSuccessful) {
                    response.body()?.let {
                        if (!it.error) {
                            _registrationResult.postValue(AuthResult.Success(it))
                        } else {
                            _registrationResult.postValue(
                                AuthResult.Error(
                                    it.message,
                                    serverError = true
                                )
                            )
                        }
                    } ?: _registrationResult.postValue(AuthResult.Error("Empty response body"))
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Registration failed: ${response.code()}"
                    _registrationResult.postValue(AuthResult.Error(errorMsg, serverError = true))
                }
            } catch (e: Exception) {
                _registrationResult.postValue(AuthResult.Error("Network error or exception: ${e.message}"))
            }
        }
    }

    fun forgotPassword(email: String) {
        _forgotPasswordResult.value = AuthResult.Loading
        viewModelScope.launch {
            try {
                val requestBody = mapOf("email" to email)
                val response = apiService.forgotPassword(requestBody)
                if (response.isSuccessful) {
                    response.body()?.let {
                        if (!it.error) {
                            _forgotPasswordResult.postValue(AuthResult.Success(it))
                        } else {
                            _forgotPasswordResult.postValue(
                                AuthResult.Error(
                                    it.message,
                                    serverError = true
                                )
                            )
                        }
                    } ?: _forgotPasswordResult.postValue(AuthResult.Error("Empty response body"))
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Request failed: ${response.code()}"
                    _forgotPasswordResult.postValue(AuthResult.Error(errorMsg, serverError = true))
                }
            } catch (e: Exception) {
                _forgotPasswordResult.postValue(AuthResult.Error("Network error or exception: ${e.message}"))
            }
        }
    }
}