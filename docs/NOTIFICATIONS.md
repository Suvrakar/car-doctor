# Notifications

Car Doctor AI has two kinds of notifications. Both also appear in the in-app **Notifications** list
(bell on Home), with unread counts, and open the right screen when tapped.

| Kind | Made by | Needs internet / server | Examples |
|---|---|---|---|
| Reminders and alerts | the phone (WorkManager, daily) | no | "My Pulsar: service due", "Tax token expires in 7 days", "Mileage dropped 18%", "Live check found 2 problems" |
| News & tips | **Firebase Cloud Messaging**, sent by you from the Firebase Console | Firebase only, no server of your own | "Monsoon tip: check your wiper blades", "Recall: …", "Eid offer at partner garages" |

Personal reminders deliberately stay on the phone: they are free, private, and arrive offline.

## Sending a message from the Firebase Console

1. Firebase Console → **Engage → Messaging → New campaign → Notifications**.
2. Write the title and text.
3. **Target → Topic**, using one of the topics the app subscribes to:

   | Topic | Who |
   |---|---|
   | `all` | everyone with "News & tips" on |
   | `lang_en`, `lang_bn`, `lang_hi`, `lang_ur`, `lang_ne`, `lang_ar`, `lang_zh`, `lang_ja`, `lang_ru`, `lang_de`, `lang_it` | by app language: send each language its own text |
   | `type_motorcycle`, `type_scooter`, `type_car`, `type_suv`, `type_cng_auto`, `type_easy_bike`, `type_e_scooter`, `type_microbus`, `type_bus`, `type_pickup`, `type_truck`, `type_other` | people who own that vehicle type |
   | `plan_free`, `plan_pro` | by plan |

   Combine with a condition, e.g. `'lang_bn' in topics && 'type_car' in topics`.
4. **Additional options → Custom data** (recommended):
   - `route`: the screen to open. One of `home`, `inbox`, `settings`, `help`, `fleet`, `garages`, or a
     vehicle screen `vehicle`, `history`, `obd`, `docs`, `service`, `fuel`, `checks`, `symptoms`,
     `vehicleid` (opens for the vehicle the user used last).
   - `title` and `body`: repeat the text here, so the message also lands in the in-app list when the
     app was closed (Android shows the system notification by itself and does not run app code).
5. Publish.

**Behaviour:** app closed → system notification on the "News & tips" channel; app open → an in-app
banner slides in at the top for 6 seconds instead. Either way it is kept in the Notifications list.
Users turn news off in **Settings → Notifications** (this unsubscribes every topic).

## Messaging one user (optional, later)

When a user is already signed in (cloud backup or AI), the app saves its FCM token at
`users/{uid}/devices/{id}` (owner-only, see `firestore.rules`). A Cloud Function can then send to one
person, for example when a garage marks a job done. This needs the Blaze plan; nothing in the app
depends on it.

## Setup checklist

- `google-services.json` in `app/` (without it Firebase, and therefore news, is simply off).
- Android 13+: the app asks for notification permission on first launch.
- Topics appear in the Console a few hours after the first devices subscribe.
