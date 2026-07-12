package com.wjx871.population.application;

/** Business-specific validation invoked immediately before a draft is submitted. */
public interface ApplicationSubmissionValidator {
    boolean supports(BusinessType businessType);
    void validate(BusinessApplication application);
}
