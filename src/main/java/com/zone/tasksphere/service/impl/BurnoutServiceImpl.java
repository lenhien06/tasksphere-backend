package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.ai.config.LlmClient;
import com.zone.tasksphere.dto.request.BurnoutAiRequest;
import com.zone.tasksphere.dto.request.BurnoutAnalyzeRequest;
import com.zone.tasksphere.dto.response.BurnoutAiResponse;
import com.zone.tasksphere.dto.response.BurnoutAnalyzeResponse;
import com.zone.tasksphere.dto.response.BurnoutDataPoint;
import com.zone.tasksphere.service.BurnoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class BurnoutServiceImpl implements BurnoutService {

    private static final int TOTAL_DAYS = 180;
    // Burnout streak: day 60–74 (0-indexed: 59–73), length = 15
    private static final int BURNOUT_START_IDX = 59;
    private static final int BURNOUT_LENGTH = 15;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String SYS_PROMPT =
            "Bạn là Engineering Manager viết tin nhắn Slack 1-on-1 thấu cảm, tự nhiên, không trách móc.";

    private final LlmClient llmClient;

    @Override
    public List<BurnoutDataPoint> generateDemoData(String developerName) {
        LocalDate startDate = LocalDate.now().minusDays(TOTAL_DAYS - 1);
        // Fixed seed → same data every call, BGK can predict the burnout zone
        Random rnd = new Random(2026L);
        List<BurnoutDataPoint> result = new ArrayList<>();

        for (int i = 0; i < TOTAL_DAYS; i++) {
            double leadTime;

            if (i >= BURNOUT_START_IDX && i < BURNOUT_START_IDX + BURNOUT_LENGTH) {
                // Deliberate burnout streak: 4.0h → 12.0h (monotonically increasing)
                int offset = i - BURNOUT_START_IDX;
                double base = 4.0 + (offset * 8.0 / (BURNOUT_LENGTH - 1));
                // Tiny noise that never breaks the increasing sequence
                double noise = rnd.nextDouble() * 0.15;
                leadTime = round1(base + noise);
            } else {
                // Normal workdays: 1.5h – 6.5h with random noise
                leadTime = round1(1.5 + rnd.nextDouble() * 5.0);
            }

            result.add(BurnoutDataPoint.builder()
                    .day(i + 1)
                    .date(startDate.plusDays(i).format(DATE_FMT))
                    .avgLeadTimeHours(leadTime)
                    .build());
        }
        return result;
    }

    @Override
    public BurnoutAnalyzeResponse analyze(BurnoutAnalyzeRequest request) {
        List<Double> leadTimes = request.getLeadTimes();
        List<String> dates = request.getDates();
        int n = leadTimes.size();

        // Longest Increasing Subarray — O(n)
        int bestStart = 0, bestLen = 1;
        int curStart = 0, curLen = 1;

        for (int i = 1; i < n; i++) {
            if (leadTimes.get(i) > leadTimes.get(i - 1)) {
                curLen++;
            } else {
                curStart = i;
                curLen = 1;
            }
            if (curLen > bestLen) {
                bestLen = curLen;
                bestStart = curStart;
            }
        }

        int endIndex = bestStart + bestLen - 1;

        List<BurnoutDataPoint> data = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            data.add(BurnoutDataPoint.builder()
                    .day(i + 1)
                    .date(safeGet(dates, i))
                    .avgLeadTimeHours(leadTimes.get(i))
                    .build());
        }

        return BurnoutAnalyzeResponse.builder()
                .data(data)
                .developerName(request.getDeveloperName())
                .startIndex(bestStart)
                .endIndex(endIndex)
                .length(bestLen)
                .startLeadTime(leadTimes.get(bestStart))
                .endLeadTime(leadTimes.get(endIndex))
                .startDate(safeGet(dates, bestStart))
                .endDate(safeGet(dates, endIndex))
                .startDay(bestStart + 1)
                .endDay(endIndex + 1)
                .build();
    }

    @Override
    public BurnoutAiResponse generateAiMessage(BurnoutAiRequest request) {
        String dev = request.getDeveloperName() != null ? request.getDeveloperName() : "bạn";
        int streakDays = request.getEndDay() - request.getStartDay() + 1;

        String userPrompt = String.format(
                """
                Dữ liệu hệ thống ghi nhận: developer %s có Lead Time tăng liên tục trong %d ngày liên tiếp \
                (từ ngày %d đến ngày %d, tức %s đến %s). \
                Lead Time tăng từ %.1fh lên %.1fh — gấp %.1f lần.

                Viết tin nhắn Slack 1-on-1 gửi cho %s. Yêu cầu:
                - Mở đầu thân thiện, tự nhiên như đồng nghiệp thân (không quá formal)
                - Đề cập nhẹ nhàng rằng bạn nhận thấy dữ liệu công việc có dấu hiệu bất thường
                - Hỏi thăm sức khỏe tinh thần, không hề trách móc
                - Gợi ý cụ thể buổi cafe lúc %s hôm nay hoặc ngày gần nhất để trao đổi, sẵn sàng điều phối lại task
                - Kết thúc ấm áp, tích cực
                - Khoảng 5–7 câu. Viết tiếng Việt. Chỉ trả về nội dung tin nhắn, không thêm gì khác.
                """,
                dev, streakDays,
                request.getStartDay(), request.getEndDay(),
                request.getStartDate(), request.getEndDate(),
                request.getStartLeadTime(), request.getEndLeadTime(),
                request.getEndLeadTime() / request.getStartLeadTime(),
                dev,
                request.getCoffeeTime() != null ? request.getCoffeeTime() : "15:00"
        );

        try {
            String msg = llmClient.call(SYS_PROMPT, userPrompt);
            return BurnoutAiResponse.builder()
                    .message(msg.trim())
                    .developerName(dev)
                    .build();
        } catch (Exception e) {
            log.error("[Burnout] AI message generation failed, using fallback", e);
            return BurnoutAiResponse.builder()
                    .message(String.format(
                            "Hi %s, dạo này anh thấy em có vẻ đang overwork. " +
                            "Data hệ thống cho thấy trong %d ngày qua, các task tốn của em gấp đôi thời gian so với bình thường. " +
                            "Anh không có ý giục deadline — anh chỉ lo em đang bị burnout vì ôm đồm quá. " +
                            "Chiều nay 3h mình đi cafe góc công ty nhé, xem anh có thể san sẻ bớt task nào cho em không.",
                            dev, streakDays))
                    .developerName(dev)
                    .build();
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String safeGet(List<String> list, int idx) {
        return (list != null && idx < list.size()) ? list.get(idx) : "";
    }
}
