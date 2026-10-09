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

**Refactor or regenerate?** Refactoring was the better call. Only one of the six
notification texts is pinned (`NotificationHubTest`), and none of the edge rules
were pinned before my test: the recurring `<=`, the 26-week cap, the one-day
block rule, and cancel-forward. A regeneration could break all of them and still
pass 35/35. The code is young, with a single commit, which makes regenerating
cheap. But the spec is one README paragraph, so the code is the only record of
those rules, and the class reaches every store write, every report, and every
member's inbox.

**What would flip your answer.** I would regenerate if every rule above were
written down and pinned by a test, including all six notification texts,
because then the suite, not the old code, would be the spec.

---

## Milestone 2: The pattern critique

### The patterns present

- **Singleton:** `NotifierFactory.getInstance()`.
- **Factory:** `NotifierFactory.createStrategy()`.
- **Strategy:** `NotificationStrategy`, `EmailNotificationStrategy`, held in
  `NotificationHub.strategy`.
- **Observer:** `NotificationHub.subscribe/publish`, `NotificationSubscriber`,
  `OutboxSubscriber`.
- **Adapter:** `OutboxSubscriber`, which turns `onNotification` into
  `Outbox.append`.

### The problem each one solves

- **Singleton:** there must be exactly one shared instance, because it holds
  state that would break if duplicated.
- **Factory:** which class to build depends on runtime information such as
  config, and callers shouldn't make that choice.
- **Strategy:** there are several ways to render a message, chosen per hub or
  per message.
- **Observer:** several independent receivers react to the same event, and the
  publisher doesn't know who they are.
- **Adapter:** an existing class has the wrong interface for its caller.

### Which of those problems exist here

None of them.
- **Singleton:** `NotifierFactory` has no state besides `instance`
  (`NotifierFactory.java:6`). The only thing that needs one instance is the
  test `factoryHandsBackTheSameInstance`.
- **Factory:** `createStrategy()` takes no input and always returns
  `EmailNotificationStrategy` (`:19-21`). Its single caller is
  `NotificationHub.java:22`.
- **Strategy:** there is one implementation, and nothing can choose another,
  because the hub hardwires it through the factory (`NotificationHub.java:22`).
- **Observer:** there is one subscriber, added by the hub's own constructor
  (`NotificationHub.java:23`). `subscribe()` has no caller outside `notify/`.
- **Adapter:** it exists only to fit the Observer interface.

### The simpler structure

**Your proposal.** `NotificationHub` with one method,
`publish(NotificationMessage m)`, which does
`outbox.append("To: " + m.recipient() + " | Subject: " + m.subject() + " | " + m.body())`.
It keeps `getOutbox()`, and `Outbox` and `NotificationMessage` stay as they are.

**What stays the same.** These tests must stay green:
- `publishedMessageLandsInTheOutboxFullyRendered`
- `aConfirmationFromTheWorkflowReachesTheOutbox`
- every workflow test that counts `hub.getOutbox().size()`

These two tests pin structure rather than behavior:
- `hubDeliversToItsOneSubscriber`
- `factoryHandsBackTheSameInstance`

**What you would keep, if anything.** None of the interfaces. `BookingWorkflow`
only calls `publish`, and the tests only read the outbox.

### What would bring each layer back

- **Observer:** facilities asks for every "Room blocked" message to also go to
  a Slack channel and an audit log. Each new receiver should be attached
  without editing the hub.
- **Strategy:** members can choose SMS instead of email. The same message then
  has to render differently depending on the recipient.
- **Factory:** the format depends on deployment config, for example plain text
  in dev and HTML email in production.

**Misuse or anti-pattern?** This is misuse. The patterns are built correctly,
but none of them has the problem it is meant to solve here. The distinction
matters because you fix misuse by deleting the layer until its requirement
shows up. The pattern itself stays a good tool. The Singleton comes closest to
an anti-pattern: it is global state that stops a test from injecting a
different factory.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Decorator, one wrapper per pricing rule, fits `PriceCalculator`
because `price()` hardcodes four ordered adjustments (base rate, weekend +25%,
long-booking -10%, tier discount, lines 30-44). Adding, removing, or reordering
a rule, such as a holiday surcharge, means editing that one method, when it
could be a new wrapper stacked on the others.

**Would you apply it today?** No. There are only four stable rules in about 15
readable lines, and `everyRuleAppliesInOrder` already pins their order, so the
extra classes would be the same speculative structure Milestone 2 criticizes.
