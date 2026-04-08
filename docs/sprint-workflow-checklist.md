# Sprint Workflow Checklist

## Da co trong he thong

- Cho phep tao nhieu sprint o trang thai `PLANNED`.
- Validate sprint nam trong thoi gian du an.
- Validate sprint khong duoc overlap voi sprint khac.
- Chi cho phep duy nhat 1 sprint `ACTIVE` trong cung project.
- PM co the dua task vao sprint tu backlog.
- Khi them task vao sprint `ACTIVE`, he thong bat canh bao xac nhan.
- Ho tro complete sprint voi carry-over:
  - `MOVE_TO_BACKLOG`
  - `MOVE_TO_NEXT_SPRINT`
- Goi y assignee bang AI dua tren skill va weekly capacity.

## Da fix trong dot nay

- Them first-class status `READY_FOR_TEST` va `TESTING` cho task workflow.
- Backend khoa QA workflow o tat ca cac duong doi trang thai:
  - `updateTask`
  - `updateStatus`
  - `updatePosition` (drag/drop)
- Khong cho task nhay thang sang `DONE` neu chua qua `IN_REVIEW`.
- Chi PM/Admin hoac thanh vien co skill QA/Testing moi duoc:
  - chuyen task sang `DONE`
  - tra task tu `IN_REVIEW` ve `IN_PROGRESS` hoac `TODO`
- UI task detail hien `Report Bug` theo skill QA/Testing thay vi an trong menu PM-only.
- Bug ticket duoc tao thanh task doc lap va lien ket voi task goc bang linked issue, khong con tao theo `parentTaskId`.
- Khi tao bug, task goc duoc day nguoc ve `IN_PROGRESS`.
- Kiem tra skill trong Kanban/task detail da dong bo theo `skillTags` va fallback du lieu cu.
- Backend chuan hoa taxonomy skill khi luu:
  - alias tester/qa -> `Testing`
  - alias developer/dev -> `Development`
- Validation overlap sprint da khong bo qua sprint `COMPLETED`.
- Start sprint tra ve loi conflict co cau truc ro rang hon khi da ton tai sprint `ACTIVE`.
- Co scheduler auto-close sprint qua han theo time-box.
- Khi sprint qua han bi auto-close, task chua xong duoc dua ve Backlog de khong keo dai sprint.

## Chua full 100% theo spec

- Van giu `IN_REVIEW` nhu status legacy de tuong thich du lieu cu, nhung workflow moi uu tien `READY_FOR_TEST` va `TESTING`.
- Auto-close sprint qua han hien dang chon huong an an toan mac dinh: dua viec ton ve Backlog thay vi hoi PM popup vi day la luong scheduler.

## Ghi chu test

- Backend compile pass.
- Frontend build pass.
- `TaskServiceImplTest` van con 1 test timeline cu dang fail khong lien quan truc tiep den sprint/QA workflow: `getTimelineView_returnsTasksAndDependencies`.
