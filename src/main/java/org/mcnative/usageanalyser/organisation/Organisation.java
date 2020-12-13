package org.mcnative.usageanalyser.organisation;

public class Organisation {

    private final String id;
    private final Subscription subscription;

    public Organisation(String id, Subscription subscription) {
        this.id = id;
        this.subscription = subscription;
    }

    public String getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }
}
