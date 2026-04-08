package com.zone.tasksphere.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.ai.config.LlmClient;
import com.zone.tasksphere.dto.response.BurndownResponse;
import com.zone.tasksphere.dto.response.BurnupReportResponse;
import com.zone.tasksphere.dto.response.ReportInsightResponse;
import com.zone.tasksphere.dto.response.VelocityForecastResponse;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.service.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAiInsightService {

    private static final String MODEL_NAME = "gemini-2.5-flash";

    private static final String SYS_PROMPT = """
            Bạn là chuyên gia PMO/Agile analyst cho hệ thống quản lý dự án.
            Nhiệm vụ: đọc dữ liệu báo cáo thật được cung cấp và viết một đoạn chẩn đoán ngắn gọn bằng tiếng Việt.

            Bắt buộc:
            - Chỉ dùng dữ liệu được cung cấp, tuyệt đối không bịa thêm số liệu.
            - Tối đa 4 câu, giọng điệu chuyên nghiệp, dễ hiểu với PM.
            - Nêu rõ rủi ro chính nếu có và một hành động tiếp theo cụ thể.
            - Không dùng markdown, không bullet, không tiêu đề.
            - Nếu dữ liệu chưa đủ để kết luận mạnh, nói rõ dữ liệu còn mỏng và đề xuất cách theo dõi tiếp.
            """;

    private final LlmClient llmClient;
    private final SprintService sprintService;
    private final SprintRepository sprintRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    public ReportInsightResponse generateInsight(
            UUID projectId,
            UUID sprintId,
            String reportType,
            UUID currentUserId
    ) {
        String normalizedType = normalizeType(reportType);
        return switch (normalizedType) {
            case "BURNDOWN" -> analyzeBurndown(projectId, sprintId, currentUserId);
            case "BURNUP" -> analyzeBurnup(projectId, sprintId, currentUserId);
            case "VELOCITY" -> analyzeVelocity(projectId, currentUserId);
            default -> throw new BadRequestException("Loại báo cáo không hợp lệ");
        };
    }

    private ReportInsightResponse analyzeBurndown(UUID projectId, UUID sprintId, UUID currentUserId) {
        Sprint sprint = requireSprint(projectId, sprintId);
        BurndownResponse data = sprintService.getBurndown(sprintId, currentUserId);

        if (data.getIdealLine() == null || data.getIdealLine().isEmpty()) {
            return fallback(
                    "BURNDOWN",
                    "AI analysis",
                    "Sprint này chưa có đủ lịch sử đốt việc để AI đưa ra nhận định đáng tin cậy. Hãy tiếp tục cập nhật trạng thái task hằng ngày để đường Remaining phản ánh đúng tiến độ thực tế."
            );
        }

        String prompt = buildPrompt("Sprint Burndown", objectToJson(data), """
                Hãy xác định sprint đang nhanh hơn hay chậm hơn đường lý tưởng, có dấu hiệu đứng yên ở cuối sprint hay không,
                và kết thúc bằng một khuyến nghị ngắn cho PM.
                """);

        return completeOrFallback(
                "BURNDOWN",
                "AI analysis",
                prompt,
                buildBurndownFallback(data)
        );
    }

    private ReportInsightResponse analyzeBurnup(UUID projectId, UUID sprintId, UUID currentUserId) {
        Sprint sprint = requireSprint(projectId, sprintId);
        BurnupReportResponse data = sprintService.getBurnup(sprintId, currentUserId);

        if (data.getData() == null || data.getData().isEmpty()) {
            return fallback(
                    "BURNUP",
                    "AI analysis",
                    "Sprint này chưa có đủ dữ liệu phạm vi và hoàn thành để AI kết luận. Khi sprint bắt đầu ghi nhận task hoàn thành hoặc thay đổi phạm vi, báo cáo sẽ phản ánh rõ hơn nguy cơ scope creep."
            );
        }

        String prompt = buildPrompt("Burnup Report", objectToJson(data), """
                Hãy tập trung phát hiện scope creep, mức độ hoàn thành so với tổng phạm vi hiện tại, và nêu ra hành động quản trị sprint phù hợp.
                """);

        return completeOrFallback(
                "BURNUP",
                "AI analysis",
                prompt,
                buildBurnupFallback(data)
        );
    }

    private ReportInsightResponse analyzeVelocity(UUID projectId, UUID currentUserId) {
        VelocityForecastResponse data = sprintService.getVelocityForecast(projectId, 5, currentUserId);

        if (data.getSprints() == null || data.getSprints().isEmpty()) {
            return fallback(
                    "VELOCITY",
                    "AI analysis",
                    "Dự án chưa có sprint đã đóng nên AI chưa có lịch sử năng suất để phân tích. Sau khi hoàn thành một vài sprint, báo cáo này sẽ giúp PM đặt mức cam kết sát hơn với năng lực thật của team."
            );
        }

        String prompt = buildPrompt("Velocity Report", objectToJson(data), """
                Hãy đánh giá mức cam kết so với năng lực hoàn thành qua các sprint gần đây, nêu xu hướng năng suất,
                và đề xuất mức độ thận trọng cho lần lập kế hoạch kế tiếp.
                """);

        return completeOrFallback(
                "VELOCITY",
                "AI analysis",
                prompt,
                buildVelocityFallback(data)
        );
    }

    private Sprint requireSprint(UUID projectId, UUID sprintId) {
        if (sprintId == null) {
            throw new BadRequestException("Sprint là bắt buộc cho báo cáo này");
        }
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));
        if (!sprint.getProject().getId().equals(projectId)) {
            throw new BusinessRuleException("Sprint không thuộc dự án này");
        }
        return sprint;
    }

    private ReportInsightResponse completeOrFallback(
            String reportType,
            String title,
            String prompt,
            String fallbackText
    ) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return fallback(reportType, title, fallbackText);
        }

        try {
            String analysis = llmClient.call(SYS_PROMPT, prompt).trim();
            if (analysis.isBlank()) {
                return fallback(reportType, title, fallbackText);
            }
            return ReportInsightResponse.builder()
                    .reportType(reportType)
                    .title(title)
                    .analysis(analysis)
                    .aiGenerated(true)
                    .model(MODEL_NAME)
                    .generatedAt(Instant.now())
                    .build();
        } catch (Exception ex) {
            log.warn("[ReportAI] Falling back to deterministic analysis for {}: {}", reportType, ex.getMessage());
            return fallback(reportType, title, fallbackText);
        }
    }

    private ReportInsightResponse fallback(String reportType, String title, String analysis) {
        return ReportInsightResponse.builder()
                .reportType(reportType)
                .title(title)
                .analysis(analysis)
                .aiGenerated(false)
                .model(null)
                .generatedAt(Instant.now())
                .build();
    }

    private String buildPrompt(String reportLabel, String payload, String instructions) {
        return """
                Loại báo cáo: %s

                Dữ liệu JSON:
                %s

                Yêu cầu phân tích:
                %s
                """.formatted(reportLabel, payload, instructions.strip());
    }

    private String objectToJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể serialize dữ liệu báo cáo cho AI", ex);
        }
    }

    private String buildBurndownFallback(BurndownResponse data) {
        double ideal = data.getIdealLine().isEmpty()
                ? 0
                : data.getIdealLine().get(data.getIdealLine().size() - 1).getRemainingPoints();
        double actual = data.getActualLine().isEmpty()
                ? data.getTotalStoryPoints()
                : data.getActualLine().get(data.getActualLine().size() - 1).getRemainingPoints();
        double gap = Math.round((actual - ideal) * 10.0) / 10.0;

        if (gap > 0) {
            return "Đường remaining thực tế đang cao hơn đường lý tưởng khoảng %s SP, cho thấy sprint đang cháy việc chậm hơn kế hoạch. PM nên tập trung kéo các task đang dở dang về Done nhanh hơn thay vì mở thêm việc mới.".formatted(formatNumber(gap));
        }
        if (gap < 0) {
            return "Đường remaining thực tế đang thấp hơn đường lý tưởng khoảng %s SP, tức team đang bám tiến độ tốt hơn kế hoạch. PM vẫn nên giữ kỷ luật đóng việc hằng ngày để tránh tăng tải vào cuối sprint.".formatted(formatNumber(Math.abs(gap)));
        }
        return "Đường remaining thực tế đang bám khá sát đường lý tưởng, nên sprint hiện không cho thấy lệch tiến độ lớn. PM nên tiếp tục theo dõi đều đặn các task đang ở trạng thái In Progress để giữ nhịp đốt việc ổn định.";
    }

    private String buildBurnupFallback(BurnupReportResponse data) {
        int scopeDelta = data.getLatestScopePoints() - data.getInitialScopePoints();
        double completionPct = data.getLatestScopePoints() > 0
                ? Math.round((data.getLatestCompletedPoints() * 1000.0 / data.getLatestScopePoints())) / 10.0
                : 0;

        if (scopeDelta > 0) {
            return "Phạm vi sprint đã tăng thêm %d SP so với lúc bắt đầu, đây là tín hiệu scope creep cần được PM kiểm soát ngay. Hiện team mới hoàn thành khoảng %s%% phạm vi hiện tại, nên mọi thay đổi mới cần được cân nhắc chuyển sang sprint sau nếu không thật sự khẩn cấp.".formatted(scopeDelta, formatNumber(completionPct));
        }
        return "Phạm vi sprint hiện khá ổn định và team đã hoàn thành khoảng %s%% tổng phạm vi đang ghi nhận. PM nên dùng burnup để tiếp tục theo dõi xem tiến độ hoàn thành có theo kịp phần việc còn lại trong những ngày cuối sprint hay không.".formatted(formatNumber(completionPct));
    }

    private String buildVelocityFallback(VelocityForecastResponse data) {
        int size = data.getSprints().size();
        VelocityForecastResponse.SprintVelocityPoint latest = data.getSprints().get(size - 1);
        int gap = latest.getCommittedPoints() - latest.getCompletedPoints();

        if (gap > 0) {
            return "Qua %d sprint gần nhất, velocity hoàn thành trung bình của team đang ở mức %s SP nhưng sprint gần nhất vẫn cam kết nhiều hơn năng lực thực thi %d SP. PM nên lấy mức completed trung bình làm baseline cho sprint tới thay vì giữ mức cam kết quá cao.".formatted(size, formatNumber(data.getAverageCompleted()), gap);
        }
        return "Qua %d sprint gần nhất, velocity hoàn thành trung bình của team đang ở mức %s SP và sprint gần nhất có mức cam kết khá sát năng lực thực tế. PM có thể dùng mức completed trung bình này làm mốc lập kế hoạch an toàn cho sprint kế tiếp.".formatted(size, formatNumber(data.getAverageCompleted()));
    }

    private String normalizeType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            throw new BadRequestException("Loại báo cáo là bắt buộc");
        }
        return reportType.trim().toUpperCase(Locale.ROOT);
    }

    private String formatNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return Double.toString(value);
    }
}
