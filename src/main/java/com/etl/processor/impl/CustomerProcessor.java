package com.etl.processor.impl;

// Transition status: LEGACY.
// Historical example only. This class is commented out, not compiled, and not part of the
// active runtime path. Prefer shared processor behavior through DefaultDynamicProcessor plus
// YAML mapping/validation rules instead of restoring scenario-specific processors here.

/*@Component
public class CustomerProcessor implements ItemProcessor<com.etl.model.source.Customers, com.etl.model.target.Customers> {
    @Override
    public com.etl.model.target.Customers process(com.etl.model.source.Customers source) {
        com.etl.model.target.Customers target = new com.etl.model.target.Customers();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setEmail(source.getEmail());
        return target;
    }
}*/

