; Copyright (c) 2018-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.internal-utils-tests
  (:require
    [clojure.test :refer [deftest is]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.internal-utils
     :refer [assoc-in! update-in! assemble-collection  non-nullable]]
    [clojure.string :as str])
  (:import
    (clojure.lang ExceptionInfo)))


(def ^:private subject
  '{:objects
    {:Ebb
     {:fields {:name {:type String}}}
     :Flow
     {:fields {:id {:type String
                    :args
                    {:show {:type Boolean}}}}}}})

(deftest assoc-in!-test
  (is (= '{:description "Ebb Desc"
           :fields {:name {:type String}}}
         (-> subject
             (assoc-in! [:objects :Ebb :description] "Ebb Desc")
             (get-in [:objects :Ebb]))))

  (when-let [e (is (thrown-with-msg? ExceptionInfo #"Intermediate key not found during assoc-in!"
                                     (-> subject
                                         (assoc-in! [:objects :Flow :fields :missing :description] "this shall fail"))))]
    (is (= '{:key :missing
             :map {:id {:args {:show {:type Boolean}}
                        :type String}}
             :more-keys (:description)
             :value "this shall fail"}
           (ex-data e)))))

(deftest update-in!-test
  (is (= {:type "String"}
         (-> subject
             (update-in! [:objects :Ebb :fields :name :type] name)
             (get-in [:objects :Ebb :fields :name]))))
  (is (= {:show {:type "Boolean Type"}}
         (-> subject
             (update-in! [:objects :Flow :fields :id :args :show :type] str " Type")
             (get-in [:objects :Flow :fields :id :args]))))

  (when-let [e (is (thrown-with-msg? ExceptionInfo #"Intermdiate key not found during update-in!"
                                     (update-in! subject [:objects :Ebb :fields :missing :description] str/upper-case)))]
    (is (= '{:key :missing
             :map {:name {:type String}}
             :more-keys (:description)}
           (ex-data e)))))


(def ^:private example
  {:data
   {:order
    {:actions {:cancel "CANCEL_NOW", :getGiftReceipt nil, :pendingReturn false, :reorder false, :resendEGiftCardToken nil, :return false, :startReturn nil}
     :amendableGroup nil
     :banners []
     :contactSellerReasons nil
     :displayId "7403200-749477"
     :giftDetails nil
     :groupCount 1
     :groups
     [{:accessPointId "39b0af7b-d854-4673-be3d-857fb69e1464"
       :actions
       {:cancel "CANCEL_NOW"
        :cancelTireInstall nil
        :changeSlot false
        :checkin nil
        :edit false
        :editDeliveryInstructions false
        :editPickupPerson true
        :editTip false
        :enableEdit nil
        :enableTip nil
        :help false
        :reorder false
        :rescheduleTireInstall nil
        :resendEGiftCard nil
        :tip false
        :track false
        :viewCancellationDetails nil}
       :addItemsText nil
       :addItemsUnavailableText nil
       :addTipMessage nil
       :allowedAmendDateTime nil
       :alternatePickupPerson nil
       :categories
       [{:accordionState nil
         :actions {:substitutions nil, :viewCancellationDetails nil}
         :banner nil
         :items
         [{:accessibilityQuantityLabel "Quantity"
           :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
           :activationCodes nil
           :count 6
           :digitalDeliveryEmailAddress nil
           :digitalDeliveryPhoneNumber nil
           :discounts nil
           :fulfilledItems nil
           :id "1"
           :isReturnable false
           :isShippedByWalmart true
           :isSubstitutionSelected true
           :itemReviewed false
           :priceInfo
           {:additionalLines nil
            :itemPrice {:displayValue "$0.50 ea", :value 0.5}
            :linePrice {:displayValue "$3.00", :value 3.0}
            :preDiscountedLinePrice nil
            :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
            :unitPrice nil}
           :product
           {:canonicalUrl "/ip/productname-wupcs/usitemid"
            :id nil
            :imageInfo
            {:thumbnailUrl
             "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
            :isAlcohol false
            :isSubstitutionEligible true
            :name "4 Naturals Pr Cherry Pie24/ 4in"
            :offerId "056136D228BC40EC9E4DD924B38145F5"
            :orderLimit 3
            :orderMinLimit 1.0
            :salesUnit "EACH"
            :salesUnitType "EACH"
            :usItemId "328433466"
            :weightIncrement 1.0}
           :protectionPlanMessage nil
           :quantity 6.0
           :quantityLabel "Qty"
           :quantityString "6"
           :returnEligibilityMessage nil
           :selectedVariants nil
           :seller nil
           :showSeller false
           :uniqueId 0
           :variantAdditionalInfo nil
           :weightUnit nil}]
         :name nil
         :returnInfo nil
         :showExtendedSubstitutions false
         :substitutions nil
         :substitutionsBanner nil
         :substitutionsBannerAction nil
         :subtext nil
         :type "REGULAR"}]
       :changeSlotIterationsLeft 0
       :cutOffTimestamp nil
       :deliveryAddress nil
       :deliveryDate nil
       :deliveryInstructions nil
       :deliveryMessage "Curbside pickup"
       :digitalDelivery nil
       :digitalDeliveryPhoneNumber nil
       :driver nil
       :editSubstitutionsCutOff nil
       :fulfillmentType "SC_PICKUP"
       :id "MjkxNzcyNTUw"
       :isAccordion false
       :isAmendInProgress false
       :isCategorized false
       :isComplete false
       :isEditSubstitutionsEligible false
       :isExpress true
       :isShippedByWalmart true
       :itemCount 6
       :items
       [{:accessibilityQuantityLabel "Quantity"
         :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
         :activationCodes nil
         :count 6
         :digitalDeliveryEmailAddress nil
         :digitalDeliveryPhoneNumber nil
         :discounts nil
         :fulfilledItems nil
         :id "1"
         :isReturnable false
         :isShippedByWalmart true
         :isSubstitutionSelected true
         :itemReviewed false
         :priceInfo
         {:additionalLines nil
          :itemPrice {:displayValue "$0.50 ea", :value 0.5}
          :linePrice {:displayValue "$3.00", :value 3.0}
          :preDiscountedLinePrice nil
          :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
          :unitPrice nil}
         :product
         {:canonicalUrl "/ip/productname-wupcs/usitemid"
          :id nil
          :imageInfo
          {:thumbnailUrl "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
          :isAlcohol false
          :isSubstitutionEligible true
          :name "4 Naturals Pr Cherry Pie24/ 4in"
          :offerId "056136D228BC40EC9E4DD924B38145F5"
          :orderLimit 3
          :orderMinLimit 1.0
          :salesUnit "EACH"
          :salesUnitType "EACH"
          :usItemId "328433466"
          :weightIncrement 1.0}
         :protectionPlanMessage nil
         :quantity 6.0
         :quantityLabel "Qty"
         :quantityString "6"
         :returnEligibilityMessage nil
         :selectedVariants nil
         :seller nil
         :showSeller false
         :uniqueId 0
         :variantAdditionalInfo nil
         :weightUnit nil}]
       :matchingItems
       [{:accessibilityQuantityLabel "Quantity"
         :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
         :activationCodes nil
         :count 6
         :digitalDeliveryEmailAddress nil
         :digitalDeliveryPhoneNumber nil
         :discounts nil
         :fulfilledItems nil
         :id "1"
         :isReturnable false
         :isShippedByWalmart true
         :isSubstitutionSelected true
         :itemReviewed false
         :priceInfo
         {:additionalLines nil
          :itemPrice {:displayValue "$0.50 ea", :value 0.5}
          :linePrice {:displayValue "$3.00", :value 3.0}
          :preDiscountedLinePrice nil
          :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
          :unitPrice nil}
         :product
         {:canonicalUrl "/ip/productname-wupcs/usitemid"
          :id nil
          :imageInfo
          {:thumbnailUrl "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
          :isAlcohol false
          :isSubstitutionEligible true
          :name "4 Naturals Pr Cherry Pie24/ 4in"
          :offerId "056136D228BC40EC9E4DD924B38145F5"
          :orderLimit 3
          :orderMinLimit 1.0
          :salesUnit "EACH"
          :salesUnitType "EACH"
          :usItemId "328433466"
          :weightIncrement 1.0}
         :protectionPlanMessage nil
         :quantity 6.0
         :quantityLabel "Qty"
         :quantityString "6"
         :returnEligibilityMessage nil
         :selectedVariants nil
         :seller nil
         :showSeller false
         :uniqueId 0
         :variantAdditionalInfo nil
         :weightUnit nil}]
       :pickupInstructions "test pickup instructions"
       :pickupPerson {:email "Abc.jain.kallukalam@walmart.com", :firstName "Abc", :lastName "Kallukalam"}
       :recipientEmailAddress nil
       :returnEligibilityMessage nil
       :seller nil
       :shipment nil
       :showSeller false
       :status
       {:helpCenterText nil
        :message {:parts [{:bold false, :lineBreak false, :nativeAction nil, :text "Ready by 10:29am Tue, May 12", :url nil}]}
        :notice nil
        :showStatusTracker true
        :statusType "PREPARING"
        :subtext nil}
       :store
       {:address
        {:addressLineOne "1313 Mockingbird Lane"
         :addressLineTwo nil
         :addressString "1313 Mockingbird Lane, Scoobyville, NX 75220"
         :city "Scoobyville"
         :country "US"
         :isAPOFPO false
         :isPOBox false
         :postalCode "75220"
         :state "NX"}
        :id 5541
        :name "Mock Store #5541"}
       :subtotal {:displayValue "$3.00", :value 3.0}
       :tipping nil
       :tireInstallationReservation nil
       :total {:displayValue "$11.20", :value 11.2}}]
     :groups_2101
     [{:accessPointId "39b0af7b-d854-4673-be3d-857fb69e1464"
       :actions
       {:cancel "CANCEL_NOW"
        :cancelTireInstall nil
        :changeSlot false
        :checkin nil
        :edit false
        :editDeliveryInstructions false
        :editPickupPerson true
        :editTip false
        :enableEdit nil
        :enableTip nil
        :help false
        :reorder false
        :rescheduleTireInstall nil
        :resendEGiftCard nil
        :tip false
        :track false
        :viewCancellationDetails nil}
       :addItemsText nil
       :addItemsUnavailableText nil
       :addTipMessage nil
       :allowedAmendDateTime nil
       :alternatePickupPerson nil
       :categories
       [{:accordionState nil
         :actions {:substitutions nil, :viewCancellationDetails nil}
         :banner nil
         :items
         [{:accessibilityQuantityLabel "Quantity"
           :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
           :activationCodes nil
           :count 6
           :digitalDeliveryEmailAddress nil
           :digitalDeliveryPhoneNumber nil
           :discounts nil
           :fulfilledItems nil
           :id "1"
           :isReturnable false
           :isShippedByWalmart true
           :isSubstitutionSelected true
           :itemReviewed false
           :priceInfo
           {:additionalLines nil
            :itemPrice {:displayValue "$0.50 ea", :value 0.5}
            :linePrice {:displayValue "$3.00", :value 3.0}
            :preDiscountedLinePrice nil
            :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
            :unitPrice nil}
           :product
           {:canonicalUrl "/ip/productname-wupcs/usitemid"
            :id nil
            :imageInfo
            {:thumbnailUrl
             "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
            :isAlcohol false
            :isSubstitutionEligible true
            :name "4 Naturals Pr Cherry Pie24/ 4in"
            :offerId "056136D228BC40EC9E4DD924B38145F5"
            :orderLimit 3
            :orderMinLimit 1.0
            :salesUnit "EACH"
            :salesUnitType "EACH"
            :usItemId "328433466"
            :weightIncrement 1.0}
           :protectionPlanMessage nil
           :quantity 6.0
           :quantityLabel "Qty"
           :quantityString "6"
           :returnEligibilityMessage nil
           :selectedVariants nil
           :seller nil
           :showSeller false
           :uniqueId 0
           :variantAdditionalInfo nil
           :weightUnit nil}]
         :name nil
         :returnInfo nil
         :showExtendedSubstitutions false
         :substitutions nil
         :substitutionsBanner nil
         :substitutionsBannerAction nil
         :subtext nil
         :type "REGULAR"}]
       :changeSlotIterationsLeft 0
       :cutOffTimestamp nil
       :deliveryAddress nil
       :deliveryDate nil
       :deliveryInstructions nil
       :deliveryMessage "Curbside pickup"
       :digitalDelivery nil
       :digitalDeliveryPhoneNumber nil
       :driver nil
       :editSubstitutionsCutOff nil
       :fulfillmentType "SC_PICKUP"
       :id "MjkxNzcyNTUw"
       :isAccordion false
       :isAmendInProgress false
       :isCategorized false
       :isComplete false
       :isEditSubstitutionsEligible false
       :isExpress true
       :isShippedByWalmart true
       :itemCount 6
       :items
       [{:accessibilityQuantityLabel "Quantity"
         :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
         :activationCodes nil
         :count 6
         :digitalDeliveryEmailAddress nil
         :digitalDeliveryPhoneNumber nil
         :discounts nil
         :fulfilledItems nil
         :id "1"
         :isReturnable false
         :isShippedByWalmart true
         :isSubstitutionSelected true
         :itemReviewed false
         :priceInfo
         {:additionalLines nil
          :itemPrice {:displayValue "$0.50 ea", :value 0.5}
          :linePrice {:displayValue "$3.00", :value 3.0}
          :preDiscountedLinePrice nil
          :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
          :unitPrice nil}
         :product
         {:canonicalUrl "/ip/productname-wupcs/usitemid"
          :id nil
          :imageInfo
          {:thumbnailUrl "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
          :isAlcohol false
          :isSubstitutionEligible true
          :name "4 Naturals Pr Cherry Pie24/ 4in"
          :offerId "056136D228BC40EC9E4DD924B38145F5"
          :orderLimit 3
          :orderMinLimit 1.0
          :salesUnit "EACH"
          :salesUnitType "EACH"
          :usItemId "328433466"
          :weightIncrement 1.0}
         :protectionPlanMessage nil
         :quantity 6.0
         :quantityLabel "Qty"
         :quantityString "6"
         :returnEligibilityMessage nil
         :selectedVariants nil
         :seller nil
         :showSeller false
         :uniqueId 0
         :variantAdditionalInfo nil
         :weightUnit nil}]
       :matchingItems
       [{:accessibilityQuantityLabel "Quantity"
         :actions {:addToCart false, :cancel "CANCEL_NOW", :configureCake false, :contactSeller false, :protectionPlan nil, :reviewItem "NOT_REVIEWABLE"}
         :activationCodes nil
         :count 6
         :digitalDeliveryEmailAddress nil
         :digitalDeliveryPhoneNumber nil
         :discounts nil
         :fulfilledItems nil
         :id "1"
         :isReturnable false
         :isShippedByWalmart true
         :isSubstitutionSelected true
         :itemReviewed false
         :priceInfo
         {:additionalLines nil
          :itemPrice {:displayValue "$0.50 ea", :value 0.5}
          :linePrice {:displayValue "$3.00", :value 3.0}
          :preDiscountedLinePrice nil
          :priceDisplayCodes {:finalCostByWeight false, :priceDisplayCondition nil, :showItemPrice true, :subtext "$0.50 ea"}
          :unitPrice nil}
         :product
         {:canonicalUrl "/ip/productname-wupcs/usitemid"
          :id nil
          :imageInfo
          {:thumbnailUrl "https://i5-qa.walmartimages.com/asr/c61a2bdf-029a-4aaa-82da-75c3fd7ae17b_6.684a19d4fdf3c7efcf5380d8f6bbd3aa.jpeg?odnWidth=180&odnHeight=180&odnBg=ffffff"}
          :isAlcohol false
          :isSubstitutionEligible true
          :name "4 Naturals Pr Cherry Pie24/ 4in"
          :offerId "056136D228BC40EC9E4DD924B38145F5"
          :orderLimit 3
          :orderMinLimit 1.0
          :salesUnit "EACH"
          :salesUnitType "EACH"
          :usItemId "328433466"
          :weightIncrement 1.0}
         :protectionPlanMessage nil
         :quantity 6.0
         :quantityLabel "Qty"
         :quantityString "6"
         :returnEligibilityMessage nil
         :selectedVariants nil
         :seller nil
         :showSeller false
         :uniqueId 0
         :variantAdditionalInfo nil
         :weightUnit nil}]
       :pickupInstructions "test pickup instructions"
       :pickupPerson {:email "Abc.jain.kallukalam@walmart.com", :firstName "Abc", :lastName "Kallukalam"}
       :recipientEmailAddress nil
       :returnEligibilityMessage nil
       :seller nil
       :shipment nil
       :showSeller false
       :status
       {:helpCenterText nil
        :message {:parts [{:bold false, :lineBreak false, :nativeAction nil, :text "Ready by 10:29am Tue, May 12", :url nil}]}
        :notice nil
        :showStatusTracker true
        :statusType "PREPARING"
        :subtext nil}
       :store
       {:address
        {:addressLineOne "1313 Mockingbird Lane"
         :addressLineTwo nil
         :addressString "1313 Mockingbird Lane, Scoobyville, NX 75220"
         :city "Scoobyville"
         :country "US"
         :isAPOFPO false
         :isPOBox false
         :postalCode "75220"
         :state "NX"}
        :id 5541
        :name "Mock Store #5541"}
       :subtotal {:displayValue "$3.00", :value 3.0}
       :tipping nil
       :tireInstallationReservation nil
       :total {:displayValue "$11.20", :value 11.2}}]
     :id "7403200749477"
     :idBarcodeImageUrl "http://localhost:8080/barcode?barWidth=1&barHeight=100&data=7403200749477"
     :isFuelPurchase false
     :isInStore false
     :paymentMethods
     [{:billingAddress
       {:address
        {:addressLineOne "2201 SE Bay Hill DR"
         :addressLineTwo "12"
         :addressString "2201 SE Bay Hill DR, 12, Bentonville, AR 72712"
         :city "Bentonville"
         :country "USA"
         :isAPOFPO false
         :isPOBox false
         :postalCode "72712"
         :state "AR"}
        :firstName "Abc Jain"
        :fullName "Abc Jain Kallukalam"
        :lastName "Kallukalam"}
       :cardNo "PIH.ccdb.VISA.CREDITCARD.30160270.1111"
       :cardType "VISA"
       :description "Ending in 1111"
       :displayValues ["$11.20"]
       :expiryDate "02-2022"
       :message nil
       :paymentType "CREDITCARD"}]
     :priceDetails
     {:authorizationAmount
      {:displayValue "$11.20"
       :info nil
       :label "Temporary hold"
       :rowInfo
       {:message
        {:parts
         [{:bold false
           :lineBreak true
           :nativeAction nil
           :text
           "The temporary hold is the amount we authorize to ensure there are funds to complete your purchase. This isn’t a charge. Your order total may vary to account for weighted items, like meat and produce, and any bag fees in your state."
           :url nil}
          {:bold false, :lineBreak true, :nativeAction nil, :text "", :url nil}
          {:bold false
           :lineBreak false
           :nativeAction nil
           :text
           "You will only be charged for the final order total once your order is picked up or delivered. Your bank should remove the authorization hold on your card within 7 days."
           :url nil}]}
        :title "Temporary hold"}
       :value 11.2}
      :belowMinimumFee {:displayValue "$7.92", :info nil, :label "Below order minimum fee", :rowInfo nil, :value 7.92}
      :discounts []
      :donations []
      :driverTip nil
      :fees []
      :grandTotal {:displayValue "$11.20", :info nil, :label "Total", :rowInfo nil, :value 11.2}
      :minimumThreshold {:displayValue "$35", :value 35.0}
      :subTotal {:displayValue "$3.00", :info nil, :label "Subtotal", :rowInfo nil, :value 3.0}
      :taxTotal {:displayValue "$0.28", :info nil, :label "Tax", :rowInfo nil, :value 0.28}}
     :receiptImage nil
     :shortTitle "May 12 order"
     :substitutionsBanner nil
     :timezone "US/Mountain"
     :tippableGroup nil
     :title "May 12, 2020 order"
     :type "GLASS"
     :version 0}}})

(deftest assemble-nested-with-vector
  (is (= {:bar {:bazz 3
                :biff 2}
          :foo 1
          :gnip [:g0
                 :g1]}
         (assemble-collection [[:foo 1]
                               [:gnip 0 :g0]
                               [:gnip 1 :g1]
                               [:bar :biff 2]
                               [:bar :bazz 3]]))))

(defn ^:private extract-paths
  "Returns a seq of terms that can be passed to assemble-paths to reconstitute the initial collection."
  ([coll]
   (extract-paths coll []))
  ([coll ks]
   (reduce-kv (fn [result k v]
                (let [ks' (conj ks k)
                      associative? (associative? v)
                      empty? (and associative? (empty? v))]
                  (if (and associative? (not empty?))
                    ;; Without this, empty lists or maps in the input are not paths in the output,
                    ;; and will be missing in the assembled collection.
                    (into result (extract-paths v ks'))
                    (conj result
                          (conj ks' v)))))
              []
              coll)))

(deftest extract-paths-test
  (is (= [[:foo 1]
          [:bar 0 :baz 2]
          [:bar 0 :biff 3]
          [:bar 1 :baz 4]
          [:bar 1 :biff 5]
          [:empty-list []]
          [:empty-map {}]]
         (extract-paths {:foo 1
                       :bar [{:baz 2
                              :biff 3}
                             {:baz 4
                              :biff 5}]
                       :empty-list []
                       :empty-map {}}))))

(deftest roundtrip
  (let [paths (extract-paths example)
        example' (assemble-collection paths)]
    (is (= example example'))))

(deftest redundancies
  (is (= {:product {:labels ["widget"
                             "frobnicator"]
                    :name "Gizmo"}}
         (assemble-collection [[:product :name "Gizmo"]
                               [:product :labels 0 "widget"]
                               [:product :labels 1 "frobnicator"]
                               ;; A fragment may add this for an empty collection (to keep the
                               ;; key from being nil or omitted).
                               [:product :labels []]]))))

(deftest maintains-order
  (is (= "#ordered/map ([:product #ordered/map ([:name :gizmo] [:alt-id :alt] [:labels [:l0 :l1 :l2]])])"
         (pr-str
           (assemble-collection [[:product :name :gizmo]
                                 [:product :alt-id :alt]
                                 [:product :labels 0 :l0]
                                 [:product :labels 1 :l1]
                                 [:product :labels 2 :l2]]
                                (ordered-map))))))

(deftest null-progation
  (is (= {:address {:city "Portland"}
          :customer {:name "Alfred"}
          :product nil}
         (assemble-collection [[:customer :name "Alfred"]
                               [:product :name "Gizmo"]
                               [:product :labels non-nullable]
                               [:product :labels nil]
                               [:address :city "Portland"]])))

  (is (= {:product {:address nil
                    :name "Frisbee"
                    :thumbnail {:full-size "big.jgp"}}}
         (assemble-collection [[:product :name "Frisbee"]
                               [:product :thumbnail :full-size "big.jgp"]
                               ;; However, since :address is nullable, it becomes null
                               ;; and :city is discarded
                               [:product :address :city "Portland"]
                               ;; e.g. (non-null (list (non-null String))
                               [:product :address :lines non-nullable]
                               [:product :address :lines 0 non-nullable]
                               [:product :address :lines 0 nil]])))

  ;; Sometimes you get nothing back at all:

  (is (= nil
         (assemble-collection [[:product non-nullable]
                               [:product :name non-nullable]
                               [:product :name nil]
                               ;; This should get discarded:
                               [:product :thumbnail "foo.jpg"]
                               ;; Because product is nulled, then the implicit root
                               ;; goes to nil, and this customer path is lost.
                               [:customer :name "Alfred"]])))

  )

(comment

  (let [paths (extract-paths example)]
    (criterium.core/bench
      (= example (assemble-collection paths))))

  ;; Base 1.70 ms

  ;; 1.62 ms -- transients

  ;; 1.76 ms -- w/ null collapsing logic added

  ;; 1.68 ms -- eliminated minor dead code

  ;; 1.59 ms -- use transients in ordered-group-by
  )