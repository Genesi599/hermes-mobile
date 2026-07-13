package com.m57.hermescontrol.ui.balance

import androidx.lifecycle.ViewModel
import com.m57.hermescontrol.data.model.ApiBalanceAccount
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.ui.common.safeLaunchLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApiBalanceUiState(
    val isLoading: Boolean = false,
    val accounts: List<ApiBalanceAccount> = emptyList(),
    val updatedAt: String? = null,
    val errorMessage: String? = null,
)

class ApiBalanceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ApiBalanceUiState())
    val uiState: StateFlow<ApiBalanceUiState> = _uiState.asStateFlow()

    fun loadBalances(forceRefresh: Boolean = false) {
        safeLaunchLoad(
            apiCall = { safeApiCall { ApiClient.hermesApi.getApiBalances(refresh = forceRefresh) } },
            onStart = { _uiState.update { it.copy(isLoading = true, errorMessage = null) } },
            onSuccess = { response ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        accounts = response.accounts,
                        updatedAt = response.updated_at,
                    )
                }
            },
            onError = { message ->
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            },
        )
    }
}
