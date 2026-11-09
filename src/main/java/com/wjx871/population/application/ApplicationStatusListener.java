package com.wjx871.population.application;

/**
 * Keeps specialized records synchronized with generic application decisions.
 *
 * <p>Draft cancellation is intentionally NOT broadcast through this interface: several
 * specialized status enums do not define {@code CANCELLED}. Business types that need a
 * cancellation hook must opt in through {@link ApplicationDraftCancellationListener}.</p>
 */
public interface ApplicationStatusListener {
    boolean supports(BusinessType businessType);
    void onStatusChanged(BusinessApplication application, ApplicationStatus status);
}
