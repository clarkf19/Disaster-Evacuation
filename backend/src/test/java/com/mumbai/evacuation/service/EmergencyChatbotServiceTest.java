package com.mumbai.evacuation.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyChatbotServiceTest {

    @Test
    void aReplyCutOffByTheLengthLimitEndsAtItsLastFullLine() {
        String cut = "### Flood\nMove to a higher floor.\n**IM";
        String reply = EmergencyChatbotService.endAtLastCompleteLine(cut);
        assertTrue(reply.startsWith("### Flood\nMove to a higher floor."), reply);
        assertFalse(reply.contains("**IM"), "the half-written last line must be dropped");
        assertTrue(reply.contains("Answer shortened"), "the user is told the answer was shortened");
    }
}
