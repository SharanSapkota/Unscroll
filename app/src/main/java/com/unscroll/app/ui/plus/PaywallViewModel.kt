package com.unscroll.app.ui.plus

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusCheck
import com.unscroll.app.domain.plus.PlusOffer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The price line: loading, the price Play returned, or Billing unavailable. */
sealed interface PriceState {
    data object Loading : PriceState
    data class Available(val offer: PlusOffer) : PriceState
    data object Unavailable : PriceState
}

/** One calm line under the buttons. */
enum class PaywallMessage { RESTORED, NOTHING_TO_RESTORE, PENDING, UNAVAILABLE, PURCHASE_FAILED }

data class PaywallUiState(
    val price: PriceState = PriceState.Loading,
    val isPlus: Boolean = false,
    val pending: Boolean = false,
    val busy: Boolean = false,
    val message: PaywallMessage? = null,
    /** Debug builds only: the last Billing response code name. Always null in release. */
    val debugStatus: String? = null,
)

private data class LocalState(
    val price: PriceState = PriceState.Loading,
    val busy: Boolean = false,
    val message: PaywallMessage? = null,
    /** Debug builds only: the last Billing response code name. Always null in release. */
    val debugStatus: String? = null,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlement: EntitlementRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PaywallUiState> = combine(
        local.asStateFlow(),
        entitlement.isPlus,
        entitlement.pending,
        entitlement.billingDebugStatus,
    ) { local, plus, pending, debugStatus ->
        PaywallUiState(local.price, plus, pending, local.busy, local.message, debugStatus)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    init {
        loadPrice()
        viewModelScope.launch {
            entitlement.purchaseFailures.collect {
                local.update { it.copy(busy = false, message = PaywallMessage.PURCHASE_FAILED) }
            }
        }
    }

    /** Asks Play for the price (also "Try again"). */
    fun loadPrice() {
        local.update { it.copy(price = PriceState.Loading) }
        viewModelScope.launch {
            val price = when (val result = entitlement.offer()) {
                is OfferResult.Available -> PriceState.Available(result.offer)
                OfferResult.Unavailable -> PriceState.Unavailable
            }
            local.update { it.copy(price = price) }
        }
    }

    /** "Continue": Play's own purchase sheet takes over. */
    fun buy(activity: Activity) {
        val offer = (local.value.price as? PriceState.Available)?.offer ?: return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = when (entitlement.purchase(activity, offer)) {
                LaunchResult.LAUNCHED -> null
                LaunchResult.ALREADY_OWNED -> PaywallMessage.RESTORED
                LaunchResult.UNAVAILABLE -> PaywallMessage.UNAVAILABLE
            }
            local.update { it.copy(busy = false, message = message) }
        }
    }

    fun restore() {
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = restoreMessage(entitlement.restore())
            local.update { it.copy(busy = false, message = message) }
        }
    }
}

/** What "Restore purchases" tells the user. */
fun restoreMessage(check: PlusCheck): PaywallMessage = when (check) {
    PlusCheck.PLUS -> PaywallMessage.RESTORED
    PlusCheck.PENDING -> PaywallMessage.PENDING
    PlusCheck.FREE -> PaywallMessage.NOTHING_TO_RESTORE
    PlusCheck.UNAVAILABLE -> PaywallMessage.UNAVAILABLE
}
