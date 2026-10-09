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

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

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
