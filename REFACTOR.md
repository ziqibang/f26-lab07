# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.**
`src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`,
`recurringSubmitSkipsAnOccurrenceThatStartsWhenAnotherEnds`. It pins that
`submit` on a RECURRING request skips an occurrence that starts at exactly the
minute an existing booking in the room ends, so the outcome has 1 skipped slot
(week 1, 09:00) and 1 booked occurrence (week 2, occurrence index 2), with the
message `"series S-1: 1 booked, 1 skipped"` and one notification per booking
written. It is a new class, green against the shipped code (36 run, 0
failures), and no existing test method was edited.

**Why that one, and does a shipped test already cover it?** `submit` has three
copies of the room-overlap check, and they disagree. REGULAR and BLOCKED use
`<` (lines 68-69, 152-153), so back-to-back slots are fine. RECURRING uses `<=`
(lines 121-122), so back-to-back counts as a conflict. Merging duplicated code
into one shared helper is the most likely move in this refactor, and it would
silently pick one operator and change series behavior. No shipped test would
notice. I grepped the test tree for `recurring` and `getSkipped`. The only
recurring submit test, `recurringSubmitBooksEveryWeekOfAnOpenSeries`, uses an
empty room, and nothing reads `getSkipped()`. The closest test,
`regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`, pins the boundary only for
REGULAR, which goes the opposite way.

**What a regeneration would do differently here.** It would decide again whether
a slot that starts when another ends is a conflict, and it would almost
certainly write one shared half-open check (`start < otherEnd && otherStart <
end`), applied to every type. `TimeSlot` documents an exclusive end, and that is
the conventional answer. A series that is skipped today would then be booked.
Whether the `<=` is a bug or a deliberate buffer between series meetings isn't
written down anywhere, and a regeneration would lose it either way without
anyone noticing.

### The directive

**The refactor and the exact directive.** Replace Conditional with
Polymorphism. The directive:

> Refactor `workflow/BookingWorkflow.java` using **Replace Conditional with
> Polymorphism**. Introduce one handler per `BookingType` (REGULAR, RECURRING,
> BLOCKED) that implements submit, cancel, price, and describe, so that no
> `switch` on booking type remains in those four methods. `BookingWorkflow`
> keeps its public constructor and method signatures and delegates to the
> handler for the type.
> **In scope:** `src/main/java/.../workflow/` only (new files allowed there).
> **Out of scope:** `domain/`, `notify/`, `pricing/`, `reporting/`, and all of
> `src/test/`. Do not edit or delete any test.
> **Preserve behavior exactly:** every message string, notification subject and
> body, and the overlap operators as written (RECURRING uses `<=`, the others
> `<`). Do not merge the overlap checks into one comparison.
> Run `mvn -B test` and show me the totals.

The boundary is `workflow/` because every caller (`ReportServiceTest`,
`NotificationHubTest`, the workflow tests) reaches this code only through
`BookingWorkflow`'s public constructor and four public methods. If those stay
fixed, nothing outside the package has a reason to change.

### The result

**The diff and the suite.** The diff is the commit "Refactor BookingWorkflow:
replace type switch with polymorphism", which comes right after the pin commit
"Pin recurring back-to-back skip before refactor". To show it, run
`git show --stat` on that commit, then `git show` for the full diff. It adds
`BookingHandler` (interface: `submit`, `cancel`, `price`, `describe`) and
`RegularBookingHandler`, `RecurringBookingHandler`, `BlockedBookingHandler`.
`BookingWorkflow` picks the handler from an `EnumMap<BookingType,
BookingHandler>`, so no `switch` remains (I grepped `workflow/` for `switch` and
`case`). Totals: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS` (35 shipped plus the pin).

**What did NOT change: behavior and files.** The pin stays green, so a series
still skips an occurrence that starts when another booking ends. The recurring
`<=` was moved as written, not merged with the `<` used by REGULAR and BLOCKED.
Two other things surprised me while reading, and both are preserved: a
recurring submit never runs the member double-booking check that REGULAR runs,
and cancelling an occurrence also cancels every later occurrence in the series.
To check that the moved code is identical, I stripped indentation and compared
every line of the three handlers against the original `BookingWorkflow.java`
from the pin commit. The only new lines are class and method headers and three
references qualified as `BookingWorkflow.FACILITIES_CONTACT` /
`BookingWorkflow.recipientFor(...)`. All message strings, notification
subjects, and comparison operators match. For files, `git status` showed
changes only under `workflow/`, and `git diff --stat` on `src/test/` is empty.
The agent stayed inside the directive.

**One thing the agent changed that you had to look at twice.** The `default:`
branches. Each of the four methods had one (`"unsupported booking type"`,
`false`, `0.0`, `"Booking #id in room"`), and the agent replaced them with
`handler == null` checks that return the same values. Like the old `default:`
branches, these checks can never run, because all three enum values are in the
map. I checked that each fallback value matched the original. I also checked
that the guards still run before the handler is called, in the same order as
before: the null request, the unknown room, and an unknown or already-cancelled
booking. A smaller change: `FACILITIES_CONTACT` and `recipientFor` went from
`private` to package-private so the handlers can use them. They are still not
visible outside `workflow/`.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
