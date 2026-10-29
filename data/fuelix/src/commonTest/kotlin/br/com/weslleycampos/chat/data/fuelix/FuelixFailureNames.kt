package br.com.weslleycampos.chat.data.fuelix

/** Which [FuelixException] this is, with its status: what the tests compare, as these have no `equals`. */
internal fun FuelixException.named(): String = when (this) {
    is FuelixException.MissingKey -> "MissingKey"
    is FuelixException.KeyRefused -> "KeyRefused"
    is FuelixException.ModelUnavailable -> "ModelUnavailable($status)"
    is FuelixException.RateLimited -> "RateLimited"
    is FuelixException.Unreachable -> "Unreachable"
    is FuelixException.Failed -> "Failed($status)"
}
