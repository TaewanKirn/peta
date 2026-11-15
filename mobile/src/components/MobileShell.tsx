import { useState, type ReactNode } from 'react'
import { icons } from '../assets'

const SHELL_KEY = 'peta.demoShell'

/** ?shell=1이면 시연용 기기 장식을 켠다. 실제 앱에서는 OS가 그리므로 기본은 꺼져 있다. */
function readShellFlag(): boolean {
  const param = new URLSearchParams(window.location.search).get('shell')
  if (param !== null) sessionStorage.setItem(SHELL_KEY, param)
  return sessionStorage.getItem(SHELL_KEY) === '1'
}

export function MobileShell({ children }: { children: ReactNode }) {
  const [demo] = useState(readShellFlag)
  return (
    <div className={demo ? 'app-column has-shell' : 'app-column'}>
      {demo && (
        <div className="device-chrome" aria-hidden="true">
          <img className="device-time" src={icons.time} alt="" />
          <span className="device-island" />
          <span className="device-cell">
            <i />
            <i />
            <i />
            <i />
          </span>
          <img className="device-wifi" src={icons.wifi} alt="" />
          <img className="device-battery" src={icons.battery} alt="" />
          <span className="device-home" />
        </div>
      )}
      {children}
    </div>
  )
}
