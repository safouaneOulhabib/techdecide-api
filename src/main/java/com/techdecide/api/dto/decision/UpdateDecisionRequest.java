package com.techdecide.api.dto.decision;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UpdateDecisionRequest {

    private String title;
    private String context;
    private String decision;
    private String consequences;
    private List<Long> tagIds;
    private List<CreateDecisionRequest.AlternativeRequest> alternatives;
    private LocalDateTime reviewDate;
}