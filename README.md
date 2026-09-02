# TownySMP Auction Displays

Paper 1.21 add-on for AxAuctions. It fills however many showcase slots you configure with the newest active listings as floating real item stacks with clickable interaction hitboxes. One slot works just as well as 12 or more.

## Install

1. Keep `auction-purchase-confirmation: true` in AxAuctions.
2. Put `TownySMPAuctionDisplays-1.0.4.jar` into the **TownySMP server's** `plugins/` folder, next to AxAuctions. Remove older versions of this add-on.
3. Fully restart the server.
4. Build as many showcases as you want. For every one, stand on the side from which players will click, look at the lower block beneath the intended item position, and run:

   ```text
   /ahdisplay set 1
   /ahdisplay set 2
   ...
   /ahdisplay set 12
   ```

The setup player's position defines the front of the showcase. The invisible click hitbox is moved toward that side so glass does not block it.
The displayed item is centred inside the block space immediately above the targeted lower block. Existing slots saved at the old default height are moved up automatically on first start.

## Commands

- `/ahdisplay set <slot>` — save or replace a showcase (slots above 12 are supported)
- `/ahdisplay <slot>` — shorthand for `/ahdisplay set <slot>`
- `/ahdisplay remove <slot>` — remove a showcase
- `/ahdisplay refresh` — immediately poll AxAuctions
- `/ahdisplay reload` — reload the plugin config and all entities
- `/ahdisplay status` — configured/occupied slot count

Admin permission: `townysmp.auctiondisplay.admin` (OP by default). Players retain AxAuctions' normal `axauctions.use` permission.

The plugin refreshes every five seconds, orders active listings newest-first, and re-fetches a listing by its AxAuctions ID at click time. AxAuctions still handles the confirmation GUI, economy withdrawal, inventory checks, messages, and final transaction.
