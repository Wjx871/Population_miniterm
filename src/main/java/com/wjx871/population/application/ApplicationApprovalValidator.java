package com.wjx871.population.application;

/** Business-specific validation invoked immediately before an approval is granted. */
public interface ApplicationApprovalValidator {
    boolean supports(BusinessType businessType);

    void validate(BusinessApplication application);
}
