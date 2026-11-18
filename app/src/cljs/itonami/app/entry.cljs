(ns itonami.app.entry
  "Tomoshibi on the device (AWAI-3502). Calls tomoshibi.suppress and
  tomoshibi.leash unchanged. Not included: mail fetch/send, the daemon, drafting
  (murakumo inference) and the governor, whose evangelism gate lives in
  kotoba-lang/kotodama, which could not be resolved on GitHub to pin it."
  (:require [itonami.app.run :as run]
            [tomoshibi.leash :as leash]
            [tomoshibi.suppress :as suppress]))

(run/register!
 {:name "stop-request" :label "配信停止の求めか判定する"
  :help "件名と本文が、配信停止・連絡不要の求めに読めるかを判定します（英語と日本語）。迷ったら止める側に倒します。"
  :example "{:subject \"Re: an invitation\" :text \"ありがとうございます。今後は配信停止でお願いします。\"}"
  :run (fn [{:keys [subject text]}] {:stop-request? (suppress/stop-request? subject text)})})

(run/register!
 {:name "suppressed" :label "停止の記録と照合する"
  :help "停止した宛先の一覧（大文字小文字は区別しません）に、この宛先が入っているかを確かめ、追記する記録の行を作ります。"
  :example "{:suppressed [\"someone@example.test\"] :email \"Someone@Example.test\" :now \"2026-11-18T00:00:00Z\"}"
  :run (fn [{:keys [suppressed email now]}]
         {:suppressed? (suppress/suppressed? (set (map #(some-> % str .toLowerCase) suppressed)) email)
          :journal-line (suppress/suppression-entry email now :stop-request)})})

(run/register!
 {:name "leash" :label "リードを判定する"
  :help "会員が出したリード（許可の記録）を、版・宛先・発行者・署名の有無・期限で判定します。どれかが合わなければ止めます。署名そのものの検証はしません。"
  :example "{:content \"{:v 1 :aud \\\"did:web:tomoshibi.itonami.cloud\\\" :by \\\"did:key:z6Mk-example\\\" :sig \\\"ab12\\\" :expiry \\\"2027-01-01T00:00:00Z\\\"}\"
 :aud \"did:web:tomoshibi.itonami.cloud\" :issuer \"did:key:z6Mk-example\" :now \"2026-11-18T00:00:00Z\"}"
  :run (fn [{:keys [content aud issuer now]}] (leash/status content {:aud aud :issuer issuer :now now}))})
