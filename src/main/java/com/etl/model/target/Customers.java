package com.etl.model.target;

import jakarta.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "Customers")
@XmlAccessorType(XmlAccessType.FIELD)
public class Customers {

    @XmlElement(name = "Customer")
    private List<Customer> customer;

    public Customers() {}

    public List<Customer> getCustomer() {
        return customer;
    }

    public void setCustomer(List<Customer> customer) {
        this.customer = customer;
    }

}
