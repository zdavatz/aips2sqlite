package com.maxl.java.shared;

/**
 * Created by maxl on 28.07.2018.
 */
public class NotaPosition implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public String pharma_code = "";
    public int quantity = 0;
    public String status = "";
    public String delivery_date = "";

    public NotaPosition() {
        // Struct-like class... 'nough said
    }
}
