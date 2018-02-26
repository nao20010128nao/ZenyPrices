package com.nao20010128nao.zenyprices

import com.neovisionaries.ws.client.WebSocketFactory
import de.bitsharesmunich.graphenej.Asset
import de.bitsharesmunich.graphenej.Converter
import de.bitsharesmunich.graphenej.LimitOrder
import de.bitsharesmunich.graphenej.api.GetLimitOrders
import de.bitsharesmunich.graphenej.interfaces.WitnessResponseListener
import de.bitsharesmunich.graphenej.models.BaseResponse
import de.bitsharesmunich.graphenej.models.WitnessResponse
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


fun findAvailableBitSharesNode(): String = bitSharesFullNodes.first {
    try {
        WebSocketFactory().createSocket(it).also {
            it.connect()
            it.disconnect()
        }
        true
    } catch (e: Throwable) {
        false
    }
}

// quote / base
fun ExecutorService.getBitSharesPair(base: Asset, quote: Asset): Future<BigDecimal?> {
    return submit(Callable {
        var result: BigDecimal? = null
        val lock = Any()
        val ws = WebSocketFactory().createSocket(findAvailableBitSharesNode())
        ws.addListener(GetLimitOrders(base.objectId, quote.objectId, 100, object : WitnessResponseListener {
            override fun onSuccess(response: WitnessResponse<*>) {
                val orders = response.result as List<LimitOrder>
                assert(!orders.isEmpty())
                for (order in orders) {
                    if (order.sellPrice.base.asset.objectId == base.objectId) {
                        order.sellPrice.base.asset.precision = base.precision
                        order.sellPrice.quote.asset.precision = quote.precision

                        val baseToQuoteExchange = getConversionRate(order.sellPrice, Converter.BASE_TO_QUOTE)
                        val quoteToBaseExchange = getConversionRate(order.sellPrice, Converter.QUOTE_TO_BASE)

                        println(String.format("> id: %s, base to quote: %.5f, quote to base: %.5f", order.objectId, baseToQuoteExchange, quoteToBaseExchange))
                    } else {
                        order.sellPrice.base.asset.precision = quote.precision;
                        order.sellPrice.quote.asset.precision = base.precision;

                        val baseToQuoteExchange = getConversionRate(order.sellPrice, Converter.BASE_TO_QUOTE);
                        val quoteToBaseExchange = getConversionRate(order.sellPrice, Converter.QUOTE_TO_BASE);
                        println(String.format("< id: %s, base to quote: %.5f, quote to base: %.5f", order.objectId, baseToQuoteExchange, quoteToBaseExchange));
                        result = if (result == null) {
                            quoteToBaseExchange
                        } else {
                            max(result!!, quoteToBaseExchange)
                        }
                    }
                }
                synchronized(lock) { lock.javaNotifyAll() }
                ws.disconnect()
            }

            override fun onError(error: BaseResponse.Error) {
                println("onError. Msg: " + error.message)
                synchronized(lock) { lock.javaNotifyAll() }
                ws.disconnect()
            }
        }))
        ws.connect()
        synchronized(lock) { lock.javaWait() }
        result
    })
}