package com.junrain.stock.order.command.infrastructure

import com.junrain.stock.order.command.application.dto.OrderPlacementDto
import com.junrain.stock.order.command.domain.OrderItem
import com.junrain.stock.order.command.domain.OrderProduct
import com.junrain.stock.order.command.domain.ProductCatalogService
import com.junrain.stock.product.command.application.ProductOrderService
import com.junrain.stock.product.command.application.dto.ProductOrderDto
import com.junrain.stock.product.command.application.dto.ProductPurchaseDto
import org.springframework.stereotype.Service

// TODO : Order의 비즈니스 로직이 얼추 완성되면 서버를 분리할 예정
@Service
class ProductCatalogServiceImpl(
    private val productOrderService: ProductOrderService
) : ProductCatalogService {
    override fun reserveProductsForOrder(orderProducts: List<OrderPlacementDto.Command.PlaceAnOrder.PlaceAnOrderProduct>): List<OrderProduct> {
        val commands = orderProducts.map {
            ProductOrderDto.Command.ReserveProducts(
                productId = it.productId,
                quantity = it.quantity
            )
        }
        // 재고 점유 및 조회
        val results = productOrderService.reserveProducts(commands)

        return results.map {
            OrderProduct(
                productId = it.productId,
                sellerId = it.sellerId,
                quantity = it.reservedQuantity,
                price = it.price
            )
        }
    }

    override fun deductStocks(orderItems: List<OrderItem>) {
        val commands = orderItems.map {
            ProductPurchaseDto.Command.Purchase(
                productId = it.productId,
                quantity = it.quantity
            )
        }

        productOrderService.deductStocks(commands)
    }
}