package com.wjx871.population.ai.precheck;

import java.time.LocalDateTime;
import java.util.List;

public record AiResolutionPreviewView(
        String taskId,
        long taskVersion,
        String resolutionStatus,
        String resolutionToken,
        LocalDateTime expiresAt,
        boolean confirmable,
        List<String> requiredSelections,
        List<PersonCandidate> personCandidates,
        List<HouseholdCandidate> targetHouseholdCandidates,
        List<Warning> warnings
) {
    public record PersonCandidate(
            String candidateToken,
            String name,
            String idCardMasked,
            Long entityVersion
    ) {
    }

    public record HouseholdCandidate(
            String candidateToken,
            String householdNoMasked,
            String regionCode,
            String addressMasked,
            String status,
            Long entityVersion
    ) {
    }

    public record Warning(String code, List<String> fieldCodes) {
    }
}
