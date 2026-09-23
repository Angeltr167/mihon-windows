package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.NetworkHelper

internal fun sourceNetworkHelper(): NetworkHelper = NetworkHelper.current()
