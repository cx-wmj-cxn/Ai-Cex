package com.aicex.marketdata.messaging;

import com.aicex.marketdata.domain.MarketEvent;
import com.aicex.marketdata.service.MarketDataFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "market.ingest.kafka", name = "enabled", havingValue = "true")
public class MarketEventConsumer {

    private final MarketDataFacade marketDataFacade;

    public MarketEventConsumer(MarketDataFacade marketDataFacade) {
        this.marketDataFacade = marketDataFacade;
    }

    @KafkaListener(
            topics = "${market.ingest.kafka.topic:market.events}",
            groupId = "${market.ingest.kafka.group-id:market-data-service}"
    )
    public void consume(MarketEvent event) {
        // Kafka 事件统一汇入 Facade，保证处理路径一致且便于扩展。
        marketDataFacade.ingestEvent(event);
    }
}
