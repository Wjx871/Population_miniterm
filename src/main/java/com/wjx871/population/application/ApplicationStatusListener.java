package com.wjx871.population.application;

/** Keeps specialized records synchronized with generic application decisions. */
public interface ApplicationStatusListener {
    boolean supports(BusinessType businessType);
    void onStatusChanged(BusinessApplication application, ApplicationStatus status);
}
