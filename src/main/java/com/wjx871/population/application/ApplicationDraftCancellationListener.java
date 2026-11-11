package com.wjx871.population.application;

/**
 * Opt-in hook for specialized business records that need to react to a DRAFT application being
 * cancelled through {@link ApplicationService#cancelDraft(Long, Integer, jakarta.servlet.http.HttpServletRequest)}.
 *
 * <p>This is deliberately separate from {@link ApplicationStatusListener}: the generic cancel path
 * must not broadcast {@code CANCELLED} to every business listener, because several specialized
 * status enums (cancellation/floating/export/key-population) do not define a {@code CANCELLED}
 * value. Only listeners that explicitly implement this interface and return {@code true} from
 * {@link #supports(BusinessType)} receive the callback.</p>
 */
public interface ApplicationDraftCancellationListener {

    boolean supports(BusinessType businessType);

    /**
     * Called inside the same transaction as the generic application DRAFT -> CANCELLED update.
     * Throwing from this method must roll back the whole cancellation, including the application row.
     */
    void onDraftCancelled(BusinessApplication application);
}
