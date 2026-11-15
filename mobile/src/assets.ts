// 모든 이미지는 assets/의 로컬 파일을 쓴다. 쓰는 파일만 번들에 포함된다.
import back from '../assets/m01_account/imgIcBack.svg'
import close from '../assets/m01_account/imgIcClose.svg'
import battery from '../assets/m01_account/imgBattery.svg'
import wifi from '../assets/m01_account/imgWiFi.svg'
import time from '../assets/m01_account/imgTime.svg'


import dogPhoto from '../assets/m03_plan1/imgAvatar.png'
import catPhoto from '../assets/m03_plan1/img20261103422531.png'
import guardianMe from '../assets/m03_plan1/img11.png'
import guardianCompanion from '../assets/m03_plan1/img21.png'
import checkWhite from '../assets/m03_plan1/imgIconCheck.svg'
import plusGray from '../assets/m03_plan1/imgIconPlus.svg'


import placeForest from '../assets/m03_plan2/imgPhoto1.png'
import placeRiver from '../assets/m03_plan2/imgPhoto.png'
import pin from '../assets/m03_plan2/imgIconPin.svg'
import chevronRight from '../assets/m03_plan2/imgIconChevronRight.svg'
import face from '../assets/m03_plan2/imgFace.svg'
import pawButton from '../assets/m03_plan2/imgIcPaw.svg'

import chevronLeft from '../assets/m03_place/imgIconChevronLeft.svg'
import chevronOrange from '../assets/m03_place/imgIconChevronRight.svg'
import iconPaw from '../assets/m03_place/imgIconPaw.svg'
import iconFence from '../assets/m03_place/imgIconFence.svg'
import iconShield from '../assets/m03_place/imgIconShield.svg'
import iconStar from '../assets/m03_place/imgIconStar.svg'
import iconInfo from '../assets/m03_place/imgIconInfo.svg'

import calendar from '../assets/m03_confirm/imgIconCalendar.svg'
import dotStart from '../assets/m03_confirm/imgDot.svg'
import dotFilled from '../assets/m03_confirm/imgDot1.svg'
import dotAdd from '../assets/m03_confirm/imgDot2.png'
import checkOrange from '../assets/m03_confirm/imgIconCheck.svg'
import plusOrange from '../assets/m03_confirm/imgIconPlus.svg'
import leaf from '../assets/m03_confirm/imgIconLeaf.svg'

import boneOn from '../assets/m04_review/img1.svg'
import boneOff from '../assets/m04_review/img5.svg'

import accountGround from '../assets/ui1/account_ground.png'
import accountDog from '../assets/ui1/account_dog.png'
import accountTree from '../assets/ui1/account_tree.png'
import accountCloudLeft from '../assets/ui1/account_cloud_l.png'
import accountCloudRight from '../assets/ui1/account_cloud_r.png'
import habitRoad from '../assets/ui1/habit_road.png'
import styleWalk from '../assets/ui1/style_walk.png'
import stylePhotoNew from '../assets/ui1/style_photo.png'
import styleOvernightNew from '../assets/ui1/style_overnight.png'
import styleSeasonsNew from '../assets/ui1/style_seasons.png'
import styleCampingNew from '../assets/ui1/style_camping.png'
import styleVacationNew from '../assets/ui1/style_vacation.png'
import optionOn from '../assets/ui1/ic_option_on.svg'
import optionOff from '../assets/ui1/ic_option_off.svg'
import dropdown from '../assets/ui1/ic_dropdown.svg'
import chipCheck from '../assets/ui1/ic_chip_check.svg'
import badgeCheck from '../assets/ui1/ic_badge_check.svg'
import camera from '../assets/ui1/ic_g12908.svg'
import plusBig from '../assets/ui1/ic_g62.svg'

import originMap from '../assets/final/origin_map.png'
import placeCozy from '../assets/final/place_cozy.png'
import fFilter from '../assets/final/ic_filter.svg'
import fChevronDown from '../assets/final/ic_chevron_down.svg'
import fPawOrange from '../assets/final/ic_paw_orange.svg'
import fBoneOrange from '../assets/final/ic_bone_orange.svg'
import fPuppy from '../assets/final/ic_puppy.svg'
import fSearch from '../assets/final/ic_search.svg'
import fMic from '../assets/final/ic_mic.svg'
import fLeaf from '../assets/final/ic_leaf.svg'
import fCheckOrange from '../assets/final/ic_check_orange.svg'

export const finalAssets = {
  originMap, placeCozy, filter: fFilter, chevronDown: fChevronDown, pawOrange: fPawOrange, boneOrange: fBoneOrange,
  puppy: fPuppy, search: fSearch, mic: fMic, leaf: fLeaf, checkOrange: fCheckOrange,
  get pin() { return icons.pin },
}

export const ui1 = {
  accountGround, accountDog, accountTree, accountCloudLeft, accountCloudRight, habitRoad,
  optionOn, optionOff, dropdown, chipCheck, badgeCheck, camera, plusBig,
  styleWalk, stylePhoto: stylePhotoNew, styleOvernight: styleOvernightNew, styleSeasons: styleSeasonsNew,
  styleCamping: styleCampingNew, styleVacation: styleVacationNew,
}

export const icons = {
  back, close, battery, wifi, time, checkWhite, plusGray, pin, chevronRight, face, pawButton, chevronLeft, chevronOrange, iconPaw, iconFence, iconShield,
  iconStar, iconInfo, calendar, dotStart, dotFilled, dotAdd, checkOrange, plusOrange, leaf, boneOn, boneOff,
}

export const photos = { dogPhoto, catPhoto, guardianMe, guardianCompanion, placeForest, placeRiver }
