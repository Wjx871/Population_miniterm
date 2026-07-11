package com.wjx871.population.application;
import java.util.EnumSet;import java.util.Set;import org.springframework.stereotype.Component;
/** Central registry for business types that require a dedicated detail record. */
@Component public class SpecializedBusinessTypeRegistry {private static final Set<BusinessType> TYPES=EnumSet.of(BusinessType.MIGRATION_IN,BusinessType.MIGRATION_OUT,BusinessType.PERSON_CANCELLATION,BusinessType.HOUSEHOLD_CANCELLATION);public boolean requiresDedicatedEntry(BusinessType type){return TYPES.contains(type);}}
