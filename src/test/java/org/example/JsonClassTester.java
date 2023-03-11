package org.example;

public class JsonClassTester {
    private String s;
    private transient String t;

    public JsonClassTester(String s, String t) {
        this.s = s;
        this.t = t;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }
}
