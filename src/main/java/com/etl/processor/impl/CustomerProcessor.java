package com.etl.processor.impl;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class CustomerProcessor implements ItemProcessor<com.etl.model.source.Customers, com.etl.model.target.Customers> {
    @Override
    public com.etl.model.target.Customers process(com.etl.model.source.Customers source) {
        com.etl.model.target.Customers target = new com.etl.model.target.Customers();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setEmail(source.getEmail());
        return target;
    }
}

