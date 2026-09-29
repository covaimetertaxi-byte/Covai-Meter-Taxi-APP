with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

terms_screen = """
@Composable
fun MandatoryTermsScreen(viewModel: TaxiMeterViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TaxiBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GeometricHeader(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TaxiWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Covai Meter Taxi Meter App – Terms & Conditions".tr,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val terms = listOf(
                    "By installing, registering, accessing, or using the Covai Meter Taxi Meter App, you agree to the following terms:".tr,
                    "1. This App is a taxi meter and trip management tool designed to assist drivers in calculating fares, tracking trips, and maintaining trip records.".tr,
                    "2. Fare amounts displayed by the App are estimates generated based on configured tariff settings, GPS data, distance, time, and waiting charges. Drivers are solely responsible for verifying, determining, and collecting the final fare from passengers.".tr,
                    "3. Drivers are solely responsible for complying with all applicable laws, permits, licences, insurance requirements, taxes, and transport regulations.".tr,
                    "4. The Company does not guarantee the accuracy of GPS signals, location tracking, distance calculations, travel time calculations, route information, or fare calculations under all circumstances.".tr,
                    "5. Drivers must verify trip details, fare details, and trip completion information before collecting payment from passengers.".tr,
                    "6. The App is only a fare calculation tool. The Company does not participate in fare negotiations, fare collection, payment recovery, or dispute resolution between drivers and passengers.".tr,
                    "7. Drivers are solely responsible for any fare disputes, refund requests, overcharge claims, undercharge claims, customer complaints, legal notices, penalties, fines, or regulatory actions arising from fares or services provided by the driver.".tr,
                    "8. Drivers are responsible for collecting toll charges, parking charges, permit charges, waiting charges, state entry taxes, and any other applicable charges from passengers where permitted by law.".tr,
                    "9. Drivers are responsible for maintaining their device, internet connection, GPS permissions, battery level, and other technical requirements necessary for proper App operation.".tr,
                    "10. Any misuse of the App, unauthorized modification, reverse engineering, fraudulent activity, false trip creation, or unlawful use may result in suspension or permanent termination of access without notice.".tr,
                    "11. The Company shall not be liable for any loss of income, business interruption, customer disputes, fare disputes, data loss, vehicle damage, accidents, penalties, legal claims, or indirect damages arising from the use of the App.".tr,
                    "12. Registration fees, activation fees, subscription fees, renewal fees, verification fees, compliance fees, and any other payments made to the Company are non-refundable.".tr,
                    "13. The Company may modify App features, tariff settings, pricing structures, policies, or these Terms & Conditions at any time without prior notice.".tr,
                    "14. Continued use of the App after any modification constitutes acceptance of the revised Terms & Conditions.".tr,
                    "15. Any disputes relating to the App shall be subject exclusively to the jurisdiction of the courts located in Coimbatore, Tamil Nadu, India.".tr,
                    "By tapping \"I Agree\", you acknowledge that you have read, understood, and accepted these Terms & Conditions and agree that all fare collection, customer interactions, legal compliance, and liabilities relating to taxi services remain solely your responsibility.".tr
                )

                for (paragraph in terms) {
                    Text(
                        text = paragraph,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Slate700,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.acceptTermsAndConditions() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp, bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = TaxiBlack),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("I Agree".tr, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
"""

content = content + terms_screen

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
