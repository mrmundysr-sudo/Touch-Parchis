# Touch Parchís — Rules (V1)

These rules are based on the **Parker Brothers Parcheesi directions** (the printed rule sheet supplied with this handoff), with one change: the **two dice are replaced by two slot reels, one reel per die**. Where the rule sheet is silent, a gap-filling default is given and marked **[GAP]**. This file is the single source of truth for the game engine. If any other handoff document disagrees, **this file wins** (see "Changes to the build instructions" at the end).

### Vocabulary: the rule sheet's words vs. our board

| Parcheesi sheet | Touch Parchís |
|---|---|
| Dice | Two slot reels (Reel 1 and Reel 2) |
| Cream spaces | Ordinary common-track squares |
| Blue spaces ("safety spaces") | The 12 brown squares: **10, 17, 22, 27, 34, 39, 44, 51, 56, 61, 68, 73** |
| Entering space | The color's start square (10, 27, 44, 61) |
| Red path | The color's 7-square home stretch |
| Maple Leaf / center | **Home Box 5** |
| Starting corner | The color's yard |
| Blockade | Two pawns of the same color on one square |

*(Verify the 12 brown squares against the plate; they were read from the approved gameplay markup.)*

---

## 1. Goal

Be the first player to bring all **four** pawns around the board, up your own home stretch, and into **Home Box 5**.

- The human player wins → **Win screen**.
- Any AI opponent gets all four pawns home first → **Loss screen**.
- The game ends the instant one player's fourth pawn reaches Home Box 5.

## 2. Players and seats

The human player is always **Red** (bottom-right yard).

| Color | Yard | Start square | Last track square | Home stretch | Role |
|---|---|---|---|---|---|
| Red | bottom-right | 10 | 73 | 74–80 | Human |
| Yellow | top-right | 27 | 22 | 81–87 | AI |
| Green | top-left | 44 | 39 | 88–94 | AI |
| Blue | bottom-left | 61 | 56 | 95–101 | AI |

**Active AI seats (decided):**
- 1 opponent → **Blue** (the cat)
- 2 opponents → **Green and Blue**
- 3 opponents → **Yellow, Green, and Blue**

Inactive seats have no pawns and are skipped. Their yards show the "Not Playing" overlay.

**Direction and turn order:** counter-clockwise (the direction the track numbers increase): **Red → Yellow → Green → Blue**, skipping inactive seats. So the order is Red → Blue with 1 opponent, Red → Green → Blue with 2, and Red → Yellow → Green → Blue with 3.

**Starting player (decided): opening spin.** The sheet says "choose a starting player." In V1 every active player spins once before the game begins, and the **highest total (Reel 1 + Reel 2) goes first**.
- The human taps Spin for their own opening spin; AI players spin automatically, one after another in turn order starting with Red. Show each player's total next to their yard.
- If two or more players tie for the highest total, **only the tied players spin again**, repeating until one player has the highest total.
- The opening spin only decides the starter. No pawns are entered or moved, doubles give no extra spin, and it does not count as anyone's turn.
- After the starter is decided, normal turns begin with the starter and continue in turn order from them. (Example, 2 opponents: if Green wins, the order is Green → Blue → Red → Green …)

Partnership play (opposite players as partners) is in the sheet but **not in V1**.

## 3. The board

- **Common track:** squares **6–73** (68 squares), shared. Pawns move in increasing number order; after 73 the track continues at 6.
- **Home stretches:** 74–101, seven squares per color. **No player may move into another color's home stretch**, so pawns there can never be captured or blocked by opponents.
- **Home Box 5:** the center, shared by all colors; it can hold any number of pawns.
- **Yard:** a pawn in the yard is **not on the board**.

## 4. Routes (the single source of truth for movement)

Each pawn has a **route index**:

| Index | Meaning |
|---|---|
| 0 | In the yard |
| 1 | On its own start square |
| 2–64 | Along the common track (64 = the color's last track square) |
| 65–71 | The seven home-stretch squares, in order |
| 72 | **Home Box 5** (finished) |

Moving *N* spaces means moving the pawn from index *i* to index *i + N*.

| Color | Index 1 → 64 (common track) | Index 65 → 71 | Index 72 |
|---|---|---|---|
| Red | 10 → 73 (no wrap) | 74, 75, 76, 77, 78, 79, 80 | Home Box 5 |
| Yellow | 27 → 73, then 6 → 22 | 81, 82, 83, 84, 85, 86, 87 | Home Box 5 |
| Green | 44 → 73, then 6 → 39 | 88, 89, 90, 91, 92, 93, 94 | Home Box 5 |
| Blue | 61 → 73, then 6 → 56 | 95, 96, 97, 98, 99, 100, 101 | Home Box 5 |

Implement routes as arrays of 73 entries (index 0 = yard). Never compute squares by arithmetic in game logic.

**You do not need an exact count to stop at the last track square.** A pawn simply continues into its home stretch. **Only Home Box 5 needs an exact count** (index 72 exactly). *(Sheet example: a pawn on the 5th stretch square needs exactly a 3 to finish.)*

## 5. The two reels

- Spinning sets **two independent values, each 1 to 6, uniformly random**. Reel 1 and Reel 2 each work like one die.
- The values are decided by the RNG at the moment Spin is pressed. The reel animation is cosmetic. The RNG must be injectable for tests.
- **Every player spins both reels on every turn**, whether or not they enter a pawn.
- Spin stays locked until the whole roll has been resolved.

**How the two values may be used** (this is the sheet's rule, unchanged):
- Move one pawn by one reel value and a (different or same) pawn by the other.
- **Or combine** the two values and move **one** pawn the total.
- A single reel value **cannot be split** to move two pawns.
- The values may be played in either order.

## 6. Entering a pawn

- A pawn can leave the yard **only on a throw of five**: **a reel showing 5, or the two reels adding up to 5** (1+4, 2+3, which uses both reels).
- The pawn goes to its color's **start square** (index 1).
- **Entering is optional.** The sheet says the player *may* enter. A 5 may be used to move a pawn already on the board instead.
- **If no five is thrown and no pawn is on the board, the turn is lost** (one spin only; there are no extra tries).
- **Two 5s (double 5)** may enter two pawns, and the player gets another spin (see §9).
- **Five plus another number:** after entering, the other reel value may move **that same pawn** or **another pawn**.
- **Entry blocked** if the start square holds a blockade (two pawns of any one color).
- **Entry onto an occupied start square (decided):** the sheet does not say, so this follows standard Parcheesi. A lone opponent pawn sitting on your start square is **captured by your entering pawn** (the one place a pawn on a safe square can be captured), and it goes back to its own yard. Your own single pawn already there simply forms a blockade with the entering pawn.

## 7. Moving a pawn

A move of *N* spaces is **legal** only if all of these are true:
1. The pawn is on the board (index 1–71). Pawns in the yard move only by the entering rule; finished pawns never move.
2. The destination index is **72 or less** (exact count to finish).
3. No **blockade** (§10) lies on any square the pawn **passes over or lands on** in the common track. This applies to the mover's own blockades too.
4. The pawn does not land on a **safety space** that already holds a pawn of a **different color** (§8).

**Passing vs. landing.** When a pawn moves the **combined total** of both reels (§5) in one move, it only *lands* on the final square. Squares in between are only *passed*. Passing over pawns, including opponents on safety spaces, is allowed unless the square holds a blockade. If a player instead plays the two values as **two separate moves with the same pawn**, the first move is a real landing and all landing rules (§7 rule 4 and captures) apply to it.

**Must move when you can.** A player must play a move if any legal move exists, and must play **as many reel values as legally possible** (so both, if both can be played). If only one value can be played, it is played and the other is lost. If no legal move exists, **the player loses the turn.** When the order or choice of moves changes how many values can be played, the engine only allows sequences that play the most values.

**Not every blockade rule is separate:** because moves are forced when possible, a player whose only legal move is to move a pawn out of their own blockade **must** do so (sheet: "he must move the blockading pieces").

## 8. Capturing and safety spaces

**Capture.** If a pawn **lands by exact count** on an ordinary (non-safety) common-track square holding a **single** opponent pawn, it **captures** that pawn: it goes back to its own yard (index 0) and must re-enter with a five. Capturing is **not mandatory**: the player picks their move. **There are no bonus moves for capturing or finishing** in this rule sheet.

**Safety spaces** (the 12 brown squares):
- A pawn resting on a safety space **cannot be captured**.
- **Two pawns of different colors may not rest on a safety space together**, so a pawn **cannot land** on a safety space holding a different color's pawn. (Pawns **may pass** over it.) The one exception is an entering pawn capturing a lone opponent on its own start square (§6).
- Two pawns of the **same** color may share a safety space, forming a blockade.

**Home stretches** are always safe: opponents cannot enter them.

## 9. Doubles

- If both reels show the same number, the player gets **another spin** after finishing the move, and keeps getting another as long as doubles keep coming.
- **Exception:** if the player **could not complete the total move** (they were unable to play both reel values), they **do not** get another spin.
- There is **no penalty** for several doubles in a row in this rule sheet.
- Doubles do **not** force a blockade to open.

## 10. Blockades

- Two pawns of the **same color** on one square of the common track (ordinary or safety) form a **blockade**.
- **No pawn, including the owner's others, can pass over or land on a blockade.**
- A blockade opens when one of its pawns moves. A player must move a blockade pawn only when it is their only legal move (§7).
- Blockades apply to the common track only. In the home stretches there are no blockades (own pawns may share squares), and Home Box 5 holds any number.

## 11. A turn, step by step

```
0. (Once per game, before turn 1) Opening spin: each active player spins once;
   highest total starts; ties among the highest re-spin among themselves only (§2).
1. Spin is enabled for the active player only.
2. Spin → Reel1 = r1, Reel2 = r2 (each 1–6).
3. List every legal action:
     Enter(pawn)                 uses a reel showing 5, or both reels if r1 + r2 = 5
     Single(pawn, reel)          moves a pawn by that reel's value
     Combined(pawn)              moves one pawn r1 + r2 as one move
4. If no action is legal → the turn is lost (go to step 7).
5. The player (or AI) takes actions until no reel values remain or none of
   the remaining values can be played. The player must play as many values
   as legally possible (§7).
6. If r1 == r2 AND both values were fully played → go back to step 2
   with the same player.
7. Pass the turn to the next active seat.
8. If a player's fourth pawn is home → the game ends (§1).
```

## 12. Controls (how a human plays one roll)

- **Spin** (the whole Mark 1 area) starts the reels. It is disabled during animation, during AI turns, and until the roll has been fully resolved.
- **Automatic when there is no choice:** the game plays a forced move automatically (only one legal action), and shows a short "No moves" message and passes the turn when nothing is legal.
- **Selecting a move.** Legal pawns are highlighted; pawns with no legal move are not tappable.
  - Tap a **yard pawn** → enters it (when a 5 is available).
  - Tap a **pawn on the board** → its legal destination squares are highlighted. Tap the **Reel 1** or **Reel 2** zone to move by that value, or tap the highlighted **total** destination to move by the combined value. If the pawn has only one legal move, it plays immediately.
  - A reel value that has been used is dimmed by a code overlay.
- **Touch targets** must be larger than the visible sprite (at least 48dp, or the nearest square plus a margin). When two pawns share a square, offset them so both are tappable.
- **Capture feedback:** show a short animation of the captured pawn returning to its yard slot.

## 13. AI opponents (V1)

The AI spins on its own after a pause of about 600–900 ms and plays its legal moves one at a time, with no hidden information or advantages. When choosing among legal actions it takes the first rule that applies, and picks randomly among ties:

1. Finish a pawn (reach index 72).
2. Capture an opponent.
3. Enter a pawn.
4. Enter the home stretch.
5. Land on a safety space.
6. Form a blockade.
7. Move the most advanced pawn that is not yet in its home stretch.

It also must obey the "play as many values as possible" rule (§7).

## 14. Reset

"Play Again" and "Try Again" return to the **Splash screen with no opponent selected**, a fresh game state, all pawns in their yard slots, and the opening spin (§2) deciding who starts.

---

## 15. Required engine tests

The rules engine is pure logic (no UI or Android dependencies) so these run on the JVM:

1. Every route has 72 entries (indices 1–72) and matches §4; no route enters another color's stretch.
2. A pawn cannot move past index 72; the last track square needs no exact stop, but Home Box 5 does.
3. Entering works with a reel showing 5 or a sum of 5, and not otherwise; it is optional; a start square blockade blocks it; a lone opponent on the start square is captured on entry.
4. Capture: exact landing on a single opponent on an ordinary square captures; safety squares and home stretches never capture; **no bonus move** is awarded.
5. A pawn cannot land on a safety square holding a different color; it can pass over it.
6. A blockade stops all pawns from passing or landing, including the owner's; a forced move out of a blockade is made only when no other legal move exists.
7. A combined move only lands at the end (intermediate squares are passed); two separate moves with the same pawn land on the intermediate square.
8. "Play as many values as possible": the engine rejects sequences that play fewer values than another available sequence.
9. Doubles give another spin only when both values were fully played; there is no three-doubles penalty.
10. Win and loss detection with 1, 2, and 3 opponents; seat order skips inactive seats, and the active seats match §2.
10a. Opening spin: the highest total starts; a tie re-spins only the tied players; the opening spin moves nothing and gives no extra spin on doubles; turn order after the starter follows §2.
11. With a seeded RNG, a full AI-vs-AI game ends with exactly one winner and never deadlocks.

---

## Changes to the build instructions

`DEEPSEEK_BUILD_INSTRUCTIONS.md` must be updated to match this file:

1. **Replace** "add them for the current pawn movement" with the use rules in §5: separate moves per reel, or one combined move of both.
2. **Replace** "72 moves total" with the route-index table (§4); Home Box 5 is index 72, and only that step needs an exact count.
3. **Add** the entering, capture, safety-space, blockade, and doubles rules and the tests (§6–§10, §15).
4. **Add** the active-seat rule for 1 / 2 / 3 opponents (Blue; Green + Blue; all three) and that Red is the human (§2).
4a. **Add** the opening-spin phase at the start of gameplay (§2): player totals shown by each yard, ties re-spin, then normal turns.
5. **Reel zones (Marks 2 and 3) are also touchable**, to choose which value to play (§12).
6. **Spin** must stay disabled until a roll is fully resolved (§5).
7. **Add** a "No moves" message and a captured-pawn return animation (§12).

## Decisions made

- **Entry onto an occupied start square:** the entering pawn captures a lone opponent there (§6).
- **Active AI seats:** 1 = Blue; 2 = Green + Blue; 3 = Yellow + Green + Blue (§2).
- **Starting player:** opening spin, highest total goes first, ties re-spin (§2).

## Still open

- **Choosing between the two values and the combined total (§12):** the current plan is tap a pawn, then tap Reel 1, Reel 2, or the highlighted total square. Change this section if you want a different interaction.
- **Partnership play, and any bonus moves** (the 10 and 20 "counting" bonuses that appear in many modern editions and in Spanish Parchís) are **not in this rule sheet** and **not in V1**.
- The sheet implies but does not state "play as many values as possible" (§7); it is implemented because the sheet says a player must move when he can.
