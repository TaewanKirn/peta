import { Navigate, Route, Routes } from 'react-router-dom'
import { MobileShell } from './components/MobileShell'
import { Account } from './screens/Account'
import { ItineraryConfirm } from './screens/ItineraryConfirm'
import { OriginPicker } from './screens/OriginPicker'
import { PetProfile1 } from './screens/PetProfile1'
import { PetProfile2 } from './screens/PetProfile2'
import { PlaceDetail } from './screens/PlaceDetail'
import { PlanParty } from './screens/PlanParty'
import { PlanTrip } from './screens/PlanTrip'
import { ReviewSave } from './screens/ReviewSave'
import { TravelStyle } from './screens/TravelStyle'

export function App() {
  return (
    <MobileShell>
      <Routes>
        <Route path="/" element={<Account />} />
        <Route path="/signup/style" element={<TravelStyle />} />
        <Route path="/signup/pet" element={<PetProfile1 />} />
        <Route path="/signup/habit" element={<PetProfile2 />} />
        <Route path="/plan/party" element={<PlanParty />} />
        <Route path="/plan/trip" element={<PlanTrip />} />
        <Route path="/plan/place/:id" element={<PlaceDetail />} />
        <Route path="/plan/itinerary" element={<ItineraryConfirm />} />
        <Route path="/plan/origin" element={<OriginPicker />} />
        <Route path="/review" element={<ReviewSave />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </MobileShell>
  )
}
