class Address {
    byte host;
    byte port;

    public Address(int host, int port) {
        this.host = (byte) host;
        this.port = (byte) port;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof Address)) return false;
        Address otherAddress = (Address) other;
        return host == otherAddress.host && port == otherAddress.port;
    }

    @Override
    public String toString() {
        return host + "." + port;
    }
}
